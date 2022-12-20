/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.protocol.mqtt;

import java.lang.invoke.MethodHandles;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.netty.handler.codec.mqtt.MqttProperties;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import org.apache.activemq.artemis.api.core.ActiveMQQueueExistsException;
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException;
import org.apache.activemq.artemis.api.core.FilterConstants;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ActiveMQMessageBundle;
import org.apache.activemq.artemis.core.server.BindingQueryResult;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.utils.CompositeAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.netty.handler.codec.mqtt.MqttProperties.MqttPropertyType.SUBSCRIPTION_IDENTIFIER;
import static org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil.DOLLAR;
import static org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil.SLASH;
import static org.apache.activemq.artemis.reader.MessageUtil.CONNECTION_ID_PROPERTY_NAME_STRING;

public class MQTTSubscriptionManager {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   private final MQTTSession session;

   private final ConcurrentMap<Long, Integer> consumerQoSLevels;

   private final ConcurrentMap<String, ServerConsumer> consumers;

   /*
    * We filter out certain messages (e.g. management messages, notifications)
    */
   private final SimpleString messageFilter;

   /*
    * We can also filter out messages from any address starting with '$'. This is because MQTT clients can do silly
    * things like subscribe to '#' which matches ever address on the broker.
    */
   private final SimpleString messageFilterNoDollar;

   private final char singleWord;

   private final char anyWords;

   public MQTTSubscriptionManager(MQTTSession session) {
      this.session = session;

      singleWord = session.getServer().getConfiguration().getWildcardConfiguration().getSingleWord();
      anyWords = session.getServer().getConfiguration().getWildcardConfiguration().getAnyWords();

      consumers = new ConcurrentHashMap<>();
//      logger.info("Creating new consumers: {}", System.identityHashCode(consumers), new Exception());
      consumerQoSLevels = new ConcurrentHashMap<>();

      // Create filter string to ignore certain messages
      StringBuilder baseFilter = new StringBuilder();
      baseFilter.append("NOT (");
      baseFilter.append("(").append(FilterConstants.ACTIVEMQ_ADDRESS).append(" = '").append(session.getServer().getConfiguration().getManagementAddress()).append("')");
      baseFilter.append(" OR ");
      baseFilter.append("(").append(FilterConstants.ACTIVEMQ_ADDRESS).append(" = '").append(session.getServer().getConfiguration().getManagementNotificationAddress()).append("')");

      StringBuilder messageFilter = new StringBuilder(baseFilter);
      messageFilter.append(")");
      this.messageFilter = new SimpleString(messageFilter.toString());

      // [MQTT-4.7.2-1]
      StringBuilder messageFilterNoDollar = new StringBuilder(baseFilter);
      messageFilterNoDollar.append(" OR ");
      messageFilterNoDollar.append("(").append(FilterConstants.ACTIVEMQ_ADDRESS).append(" LIKE '").append(DOLLAR).append("%')");
      messageFilterNoDollar.append(")");
      this.messageFilterNoDollar = new SimpleString(messageFilterNoDollar.toString());
   }

   synchronized void start() throws Exception {
      for (MqttTopicSubscription subscription : session.getState().getSubscriptions()) {
         addSubscription(subscription, null, true);
      }
   }

   private void addSubscription(MqttTopicSubscription subscription, Integer subscriptionIdentifier, boolean initialStart) throws Exception {
      String rawTopicName = CompositeAddress.extractAddressName(subscription.topicName());
      String parsedTopicName = parseTopicName(rawTopicName);
      int qos = subscription.qualityOfService().value();
      String coreAddress = MQTTUtil.convertMqttTopicFilterToCoreAddress(parsedTopicName, session.getWildcardConfiguration());

      Queue q = createQueueForSubscription(coreAddress, getQueueNameForTopic(rawTopicName));

      try {
         if (initialStart) {
            createConsumerForSubscriptionQueue(q, parsedTopicName, qos, subscription.option().isNoLocal(), null);
         } else {
            MqttTopicSubscription existingSubscription = session.getState().getSubscription(parsedTopicName);
            if (existingSubscription == null) {
               createConsumerForSubscriptionQueue(q, parsedTopicName, qos, subscription.option().isNoLocal(), null);
            } else {
               Long existingConsumerId = consumers.get(parsedTopicName).getID();
               consumerQoSLevels.put(existingConsumerId, qos);
               if (existingSubscription.option().isNoLocal() != subscription.option().isNoLocal()) {
                  createConsumerForSubscriptionQueue(q, parsedTopicName, qos, subscription.option().isNoLocal(), existingConsumerId);
               }
            }

            if (subscription.option().retainHandling() == MqttSubscriptionOption.RetainedHandlingPolicy.SEND_AT_SUBSCRIBE || (subscription.option().retainHandling() == MqttSubscriptionOption.RetainedHandlingPolicy.SEND_AT_SUBSCRIBE_IF_NOT_YET_EXISTS && existingSubscription == null)) {
               session.getRetainMessageManager().addRetainedMessagesToQueue(q, parsedTopicName);
            }

            session.getState().addSubscription(subscription, session.getWildcardConfiguration(), subscriptionIdentifier);
         }
      } catch (Exception e) {
         // if anything broke during the creation of the consumer (or otherwise) then ensure both the subscription queue and persistent subscription state is removed
         q.deleteQueue();
         session.getSessionStateManager().removeSessionState(session.getState().getClientId());
         throw e;
      }
   }

   private String parseTopicName(String rawTopicName) {
      String parsedTopicName = rawTopicName;

      // if using a shared subscription then parse
      if (rawTopicName.startsWith(MQTTUtil.SHARED_SUBSCRIPTION_PREFIX)) {
         parsedTopicName = rawTopicName.substring(rawTopicName.indexOf(SLASH, rawTopicName.indexOf(SLASH) + 1) + 1);
      }
      return parsedTopicName;
   }

   synchronized void stop() throws Exception {
      for (ServerConsumer consumer : consumers.values()) {
         consumer.setStarted(false);
         consumer.disconnect();
         consumer.getQueue().removeConsumer(consumer);
         consumer.close(false);
      }
   }

   private Queue createQueueForSubscription(String address, SimpleString queueName) throws Exception {
      // check to see if a subscription queue already exists.
      Queue q = session.getServer().locateQueue(queueName);

      // The queue does not exist so we need to create it.
      if (q == null) {
         SimpleString sAddress = SimpleString.toSimpleString(address);

         // Check we can auto create queues.
         BindingQueryResult bindingQueryResult = session.getServerSession().executeBindingQuery(sAddress);
         if (!bindingQueryResult.isAutoCreateQueues()) {
            throw ActiveMQMessageBundle.BUNDLE.noSuchQueue(sAddress);
         }

         // check that the address exists, if not we try to auto create it (if allowed).
         AddressInfo addressInfo = session.getServerSession().getAddress(sAddress);
         if (addressInfo == null) {
            if (!bindingQueryResult.isAutoCreateAddresses()) {
               throw ActiveMQMessageBundle.BUNDLE.addressDoesNotExist(SimpleString.toSimpleString(address));
            }
            addressInfo = session.getServerSession().createAddress(SimpleString.toSimpleString(address),
                                                                   RoutingType.MULTICAST, true);
         }
         return findOrCreateQueue(bindingQueryResult, addressInfo, queueName);
      }
      return q;
   }

   private Queue findOrCreateQueue(BindingQueryResult bindingQueryResult, AddressInfo addressInfo, SimpleString queue) throws Exception {
      /*
       * MQTT 3.1 and 3.1.1 clients using a clean session should have a *non-durable* subscription queue. If the broker
       * restarts the queue should be removed. This is due to [MQTT-3.1.2-6] which states that the session (and any
       * state) must last only as long as the network connection.
       */
      boolean durable = session.getVersion() == MQTTVersion.MQTT_5 || (session.getVersion() != MQTTVersion.MQTT_5 && !session.isClean());
      if (addressInfo.getRoutingTypes().contains(RoutingType.MULTICAST)) {
         return session.getServerSession().createQueue(new QueueConfiguration(queue).setAddress(addressInfo.getName()).setFilterString(getMessageFilter(addressInfo.getName())).setDurable(durable));
      }

      if (addressInfo.getRoutingTypes().contains(RoutingType.ANYCAST)) {
         if (!bindingQueryResult.getQueueNames().isEmpty()) {
            SimpleString name = null;
            for (SimpleString qName : bindingQueryResult.getQueueNames()) {
               if (name == null) {
                  name = qName;
               } else if (qName.equals(addressInfo.getName())) {
                  name = qName;
               }
            }
            return session.getServer().locateQueue(name);
         } else {
            try {
               return session.getServerSession().createQueue(new QueueConfiguration(addressInfo.getName()).setRoutingType(RoutingType.ANYCAST).setFilterString(getMessageFilter(addressInfo.getName())).setDurable(durable));
            } catch (ActiveMQQueueExistsException e) {
               return session.getServer().locateQueue(addressInfo.getName());
            }
         }
      }

      throw ActiveMQMessageBundle.BUNDLE.invalidRoutingTypeForAddress(addressInfo.getRoutingType(), addressInfo.getName().toString(), EnumSet.allOf(RoutingType.class));
   }

   private SimpleString getMessageFilter(SimpleString addressName) {
      /*
       * By the time we get here wildcards in the MQTT topic filter have already been translated into their core
       * equivalents. This check is to enforce [MQTT-4.7.2-1].
       */
      if (addressName.startsWith(singleWord) || addressName.startsWith(anyWords)) {
         return messageFilterNoDollar;
      } else {
         return messageFilter;
      }
   }

   private void createConsumerForSubscriptionQueue(Queue queue, String topic, int qos, boolean noLocal, Long existingConsumerId) throws Exception {
      long cid = existingConsumerId != null ? existingConsumerId : session.getServer().getStorageManager().generateID();

      // for noLocal support we use the MQTT *client id* rather than the connection ID, but we still use the existing property name
      ServerConsumer consumer = session.getServerSession().createConsumer(cid, queue.getName(), noLocal ? SimpleString.toSimpleString(CONNECTION_ID_PROPERTY_NAME_STRING + " <> '" + session.getState().getClientId() + "'") : null, false, false, -1);

      logger.info("Adding consumer for {} to {}: {}", parseTopicName(topic), System.identityHashCode(consumers), consumer);
      ServerConsumer existingConsumer = consumers.put(parseTopicName(topic), consumer);
      if (existingConsumer != null) {
         existingConsumer.setStarted(false);
         existingConsumer.close(false);
      }

      consumer.setStarted(true);

      consumerQoSLevels.put(cid, qos);
   }

   short[] removeSubscriptions(List<String> topics) throws Exception {
      short[] reasonCodes;

      synchronized (session.getState()) {
         reasonCodes = new short[topics.size()];
         for (int i = 0; i < topics.size(); i++) {
            reasonCodes[i] = removeSubscription(topics.get(i));
         }
      }

      return reasonCodes;
   }

   private short removeSubscription(String address) {
      return removeSubscription(address, true);
   }

   private short removeSubscription(String topic, boolean enforceSecurity) {
      if (session.getState().getSubscription(topic) == null) {
         return MQTTReasonCodes.NO_SUBSCRIPTION_EXISTED;
      }

      short reasonCode = MQTTReasonCodes.SUCCESS;

      try {
         session.getState().removeSubscription(topic);


         ServerConsumer removed = consumers.remove(parseTopicName(topic));
         logger.info("Removed consumer for {} from {}: {}", parseTopicName(topic), System.identityHashCode(consumers), removed, new Exception());
         if (removed != null) {
            removed.close(false);
            consumerQoSLevels.remove(removed.getID());
         }

         SimpleString internalQueueName = getQueueNameForTopic(topic);
         Queue queue = session.getServer().locateQueue(internalQueueName);
         if (queue != null) {
            if (queue.isConfigurationManaged()) {
               queue.deleteAllReferences();
            } else if (!topic.startsWith(MQTTUtil.SHARED_SUBSCRIPTION_PREFIX) || (topic.startsWith(MQTTUtil.SHARED_SUBSCRIPTION_PREFIX) && queue.getConsumerCount() == 0)) {
               session.getServerSession().deleteQueue(internalQueueName, enforceSecurity);
            }
         }
      } catch (Exception e) {
         MQTTLogger.LOGGER.errorRemovingSubscription(e);
         reasonCode = MQTTReasonCodes.UNSPECIFIED_ERROR;
      }

      return reasonCode;
   }

   private SimpleString getQueueNameForTopic(String topic) {
      if (topic.startsWith(MQTTUtil.SHARED_SUBSCRIPTION_PREFIX)) {
         int slashIndex = topic.indexOf(SLASH) + 1;
         String sharedSubscriptionName = topic.substring(slashIndex, topic.indexOf(SLASH, slashIndex));
         String parsedTopicName = topic.substring(topic.indexOf(SLASH, slashIndex) + 1);
         return new SimpleString(sharedSubscriptionName).concat(".").concat(parsedTopicName);
      } else {
         return new SimpleString(session.getState().getClientId()).concat(".").concat(topic);
      }
   }

   /**
    * As per MQTT Spec. Subscribes this client to a number of MQTT topics.
    *
    * @param subscriptions
    * @return An array of integers representing the list of accepted QoS for each topic.
    * @throws Exception
    */
   int[] addSubscriptions(List<MqttTopicSubscription> subscriptions, MqttProperties properties) throws Exception {
      synchronized (session.getState()) {
         Integer subscriptionIdentifier = null;
         if (properties.getProperty(SUBSCRIPTION_IDENTIFIER.value()) != null) {
            subscriptionIdentifier = (Integer) properties.getProperty(SUBSCRIPTION_IDENTIFIER.value()).value();
         }

         int[] qos = new int[subscriptions.size()];

         for (int i = 0; i < subscriptions.size(); i++) {
            try {
               addSubscription(subscriptions.get(i), subscriptionIdentifier, false);
               qos[i] = subscriptions.get(i).qualityOfService().value();
            } catch (ActiveMQSecurityException e) {
               // user is not authorized to create subsription
               if (session.getVersion() == MQTTVersion.MQTT_5) {
                  qos[i] = MQTTReasonCodes.NOT_AUTHORIZED;
               } else if (session.getVersion() == MQTTVersion.MQTT_3_1_1) {
                  qos[i] = MQTTReasonCodes.UNSPECIFIED_ERROR;
               } else {
                  /*
                   * For MQTT 3.1 clients:
                   *
                   * Note that if a server implementation does not authorize a SUBSCRIBE request to be made by a client,
                   * it has no way of informing that client. It must therefore make a positive acknowledgement with a
                   * SUBACK, and the client will not be informed that it was not authorized to subscribe.
                   *
                   *
                   * For MQTT 3.1.1 clients:
                   *
                   * The 3.1.1 spec doesn't directly address the situation where the server does not authorize a
                   * SUBSCRIBE. It really just says this:
                   *
                   * [MQTT-3.8.4-1] When the Server receives a SUBSCRIBE Packet from a Client, the Server MUST respond
                   *  with a SUBACK Packet.
                   */
                  qos[i] = subscriptions.get(i).qualityOfService().value();
               }
            }
         }
         return qos;
      }
   }

   Map<Long, Integer> getConsumerQoSLevels() {
      return consumerQoSLevels;
   }

   void clean(boolean enforceSecurity) {
      for (MqttTopicSubscription mqttTopicSubscription : session.getState().getSubscriptions()) {
         removeSubscription(mqttTopicSubscription.topicName(), enforceSecurity);
      }
   }
}
