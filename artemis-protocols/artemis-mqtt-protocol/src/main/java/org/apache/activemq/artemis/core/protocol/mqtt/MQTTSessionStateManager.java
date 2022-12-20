/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.core.protocol.mqtt;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.filter.impl.FilterImpl;
import org.apache.activemq.artemis.core.message.impl.CoreMessage;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.utils.collections.LinkedListIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MQTTSessionStateManager {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
   private ActiveMQServer server;
   private final Map<String, MQTTSessionState> sessionStates = new ConcurrentHashMap<>();
   private final Queue sessionStore;
   private static Map<Integer, MQTTSessionStateManager> INSTANCES = new HashMap<>();

   /*
    * Even though there may be multiple instances of MQTTProtocolManager (e.g. for MQTT on different ports) we only want
    * one instance of MQTTSessionStateManager per-broker with the understanding that there can be multiple brokers in
    * the same JVM.
    */
   public static synchronized MQTTSessionStateManager getInstance(ActiveMQServer server) throws Exception {
      MQTTSessionStateManager instance = INSTANCES.get(System.identityHashCode(server));
      if (instance == null) {
         instance = new MQTTSessionStateManager(server);
         INSTANCES.put(System.identityHashCode(server), instance);
      }

      return instance;
   }

   public static synchronized void removeInstance(ActiveMQServer server) {
      INSTANCES.remove(System.identityHashCode(server));
   }

   private MQTTSessionStateManager(ActiveMQServer server) throws Exception {
      this.server = server;
      sessionStore = server.createQueue(new QueueConfiguration(MQTTUtil.MQTT_SESSION_STORE).setRoutingType(RoutingType.ANYCAST).setLastValue(true).setDurable(true).setInternal(true), true);

      // load session data from queue
      try (LinkedListIterator<MessageReference> iterator = sessionStore.browserIterator()) {
         try {
            while (iterator.hasNext()) {
               MessageReference ref = iterator.next();
               String clientId = ref.getMessage().getStringProperty(Message.HDR_LAST_VALUE_NAME);
               MQTTSessionState sessionState = new MQTTSessionState((CoreMessage) ref.getMessage(), this);
               sessionStates.put(clientId, sessionState);
            }
         } catch (NoSuchElementException ignored) {
            // this could happen through paging browsing
         }
      }
   }

   public void scanSessions() {
      List<String> toRemove = new ArrayList();
      for (Map.Entry<String, MQTTSessionState> entry : sessionStates.entrySet()) {
         MQTTSessionState state = entry.getValue();
         logger.debug("Inspecting session: {}", state);
         int sessionExpiryInterval = state.getClientSessionExpiryInterval();
         if (!state.isAttached() && sessionExpiryInterval > 0 && state.getDisconnectedTime() + (sessionExpiryInterval * 1000) < System.currentTimeMillis()) {
            toRemove.add(entry.getKey());
         }
         if (state.isWill() && !state.isAttached() && state.isFailed() && state.getWillDelayInterval() > 0 && state.getDisconnectedTime() + (state.getWillDelayInterval() * 1000) < System.currentTimeMillis()) {
            state.getSession().sendWillMessage();
         }
      }

      for (String key : toRemove) {
         try {
            MQTTSessionState state = removeSessionState(key);
            if (state != null && state.isWill() && !state.isAttached() && state.isFailed()) {
               state.getSession().sendWillMessage();
            }
         } catch (Exception e) {
            // TODO: make this a real error message
            e.printStackTrace();
         }
      }
   }

   public MQTTSessionState getSessionState(String clientId) throws Exception {
      /* [MQTT-3.1.2-4] Attach an existing session if one exists otherwise create a new one. */
      if (sessionStates.containsKey(clientId)) {
         return sessionStates.get(clientId);
      } else {
         MQTTSessionState sessionState = new MQTTSessionState(clientId, this);
         logger.debug("Adding MQTT session state for: {}", clientId);
         sessionStates.put(clientId, sessionState);
         storeSessionState(sessionState);
         return sessionState;
      }
   }

   public MQTTSessionState removeSessionState(String clientId) throws Exception {
      logger.debug("Removing MQTT session state for: {}", clientId);
      if (clientId == null) {
         return null;
      }
      removeDurableSessionState(clientId);
      return sessionStates.remove(clientId);
   }

   public void removeDurableSessionState(String clientId) throws Exception {
      logger.debug("Removing durable MQTT session state for: {}", clientId);
      sessionStore.deleteMatchingReferences(FilterImpl.createFilter(new StringBuilder(Message.HDR_LAST_VALUE_NAME).append(" = '").append(clientId).append("'").toString()));
   }

   public Map<String, MQTTSessionState> getSessionStates() {
      return new HashMap<>(sessionStates);
   }

   @Override
   public String toString() {
      return "MQTTSessionStateManager@" + Integer.toHexString(System.identityHashCode(this));
   }

   public void storeSessionState(MQTTSessionState state) throws Exception {
      logger.debug("Adding durable MQTT session state for: {}", state.getClientId());
      CoreMessage message = new CoreMessage().initBuffer(50).setMessageID(server.getStorageManager().generateID());
      message.setAddress(MQTTUtil.MQTT_SESSION_STORE);
      message.setDurable(true);
      message.putStringProperty(Message.HDR_LAST_VALUE_NAME, state.getClientId());
      Collection<Pair<MqttTopicSubscription, Integer>> subscriptions = state.getSubscriptionsPlusID();
      ActiveMQBuffer buf = message.getBodyBuffer();

      /*
       * This byte represents the "version". If the payload changes at any point in the future then we can detect that
       * and adjust so that when users are upgrading we can still read the old data format.
       */
      buf.writeByte((byte) 0);

      buf.writeInt(subscriptions.size());
      logger.debug("Serializing {} subscriptions", subscriptions.size());
      for (Pair<MqttTopicSubscription, Integer> pair : subscriptions) {
         MqttTopicSubscription sub = pair.getA();
         buf.writeString(sub.topicName());
         buf.writeInt(sub.option().qos().value());
         buf.writeBoolean(sub.option().isNoLocal());
         buf.writeBoolean(sub.option().isRetainAsPublished());
         buf.writeInt(sub.option().retainHandling().value());
         buf.writeNullableInt(pair.getB());
      }
      server.getPostOffice().route(message, true);
   }
}