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

package org.apache.activemq.artemis.protocol.mqtt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTSessionState;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTSessionStateManager;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.Before;
import org.junit.Test;

public class SessionStateManagerTest extends ActiveMQTestBase {

   protected ActiveMQServer server;

   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();
      Configuration config = createDefaultInVMConfig();
      config.getAcceptorConfigurations().clear();
      server = createServer(true, config);
      server.start();
   }

   @Test(timeout = 30000)
   public void testSessionStateManager() throws Exception {
      final long SESSION_COUNT = 500;

      List<Pair<String, Collection<Pair<MqttTopicSubscription, Integer>>>> sessions = new ArrayList<>();
      for (int i = 0; i < SESSION_COUNT; i++) {
         List<Pair<MqttTopicSubscription, Integer>> subs = new ArrayList<>();
         Pair<String, Collection<Pair<MqttTopicSubscription, Integer>>> session = new Pair<>(RandomUtil.randomString(), subs);
         for (int j = 0; j < RandomUtil.randomInterval(1, 50); j++) {
            MqttTopicSubscription sub = new MqttTopicSubscription(RandomUtil.randomString(),
                                                                  new MqttSubscriptionOption(MqttQoS.valueOf(RandomUtil.randomInterval(0, 3)),
                                                                                             RandomUtil.randomBoolean(),
                                                                                             RandomUtil.randomBoolean(),
                                                                                             MqttSubscriptionOption.RetainedHandlingPolicy.valueOf(RandomUtil.randomInterval(0, 3))));
            subs.add(new Pair(sub, RandomUtil.randomPositiveIntOrNull()));
         }
         sessions.add(session);
      }

      MQTTSessionStateManager stateManager = MQTTSessionStateManager.getInstance(server);

      for (Pair<String, Collection<Pair<MqttTopicSubscription, Integer>>> session : sessions) {
         String clientId = session.getA();
         Collection<Pair<MqttTopicSubscription, Integer>> subs = session.getB();
         MQTTSessionState sessionState = new MQTTSessionState(clientId, stateManager);
         for (Pair<MqttTopicSubscription, Integer> sub : subs) {
            sessionState.addSubscription(sub.getA(), MQTTUtil.MQTT_WILDCARD, sub.getB());
         }
      }

      Wait.assertEquals(SESSION_COUNT, () -> server.locateQueue(MQTTUtil.MQTT_SESSION_STORE).getMessageCount(), 2000, 100);

      server.stop();
      server.start();

      Wait.assertEquals(SESSION_COUNT, () -> server.locateQueue(MQTTUtil.MQTT_SESSION_STORE).getMessageCount(), 2000, 100);

      stateManager = MQTTSessionStateManager.getInstance(server);

      for (Pair<String, Collection<Pair<MqttTopicSubscription, Integer>>> session : sessions) {
         String clientId = session.getA();
         Collection<Pair<MqttTopicSubscription, Integer>> mySubs = session.getB();
         Map<String, MQTTSessionState> sessionStates = stateManager.getSessionStates();
         assertTrue(sessionStates.containsKey(clientId));
         MQTTSessionState state = sessionStates.get(clientId);
         Collection<Pair<MqttTopicSubscription, Integer>> brokerSubs = state.getSubscriptionsPlusID();
         assertEquals(mySubs.size(), brokerSubs.size());
         for (Pair<MqttTopicSubscription, Integer> mySub : mySubs) {
            boolean found = false;
            for (Pair<MqttTopicSubscription, Integer> brokerSub : brokerSubs) {
               if (compareSubs(mySub.getA(), brokerSub.getA())) {
                  found = true;
                  assertEquals(mySub.getB(), brokerSub.getB());
                  break;
               }
            }
            assertTrue(found);
         }
      }
   }

   private boolean compareSubs(MqttTopicSubscription a, MqttTopicSubscription b) {
      if (a == b)
         return true;
      if (a == null || b == null)
         return false;
      if (a.topicName() == null) {
         if (b.topicName() != null)
            return false;
      } else if (!a.topicName().equals(b.topicName())) {
         return false;
      }
      if (a.option() == null) {
         if (b.option() != null)
            return false;
      } else {
         if (a.option().qos() == null) {
            if (b.option().qos() != null)
               return false;
         } else if (a.option().qos().value() != b.option().qos().value()) {
            return false;
         }
         if (a.option().retainHandling() == null) {
            if (b.option().retainHandling() != null)
               return false;
         } else if (a.option().retainHandling().value() != b.option().retainHandling().value()) {
            return false;
         }
         if (a.option().isRetainAsPublished() != b.option().isRetainAsPublished()) {
            return false;
         }
         if (a.option().isNoLocal() != b.option().isNoLocal()) {
            return false;
         }
      }

      return true;
   }
}
