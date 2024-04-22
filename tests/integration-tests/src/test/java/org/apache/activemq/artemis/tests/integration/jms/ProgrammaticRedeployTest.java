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
package org.apache.activemq.artemis.tests.integration.jms;

import javax.jms.ConnectionFactory;
import javax.jms.JMSContext;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.junit.Assert;
import org.junit.Test;

public class ProgrammaticRedeployTest extends ActiveMQTestBase {

   @Test
   /**
    * This is basically a copy of org.apache.activemq.artemis.tests.integration.jms.RedeployTest#testRedeployAddressQueue().
    * However, this test disables automatic configuration reload and uses the management API to do it instead.
    */
   public void testRedeployAddressQueue() throws Exception {
      Path brokerXML = getTestDirfile().toPath().resolve("broker.xml");
      URL url1 = ProgrammaticRedeployTest.class.getClassLoader().getResource("reload-address-queues-programmatic.xml");
      URL url2 = ProgrammaticRedeployTest.class.getClassLoader().getResource("reload-address-queues-updated-programmatic.xml");
      Files.copy(url1.openStream(), brokerXML);

      EmbeddedActiveMQ embeddedActiveMQ = new EmbeddedActiveMQ();
      embeddedActiveMQ.setConfigResourcePath(brokerXML.toUri().toString());
      embeddedActiveMQ.start();

      ConnectionFactory connectionFactory = new ActiveMQConnectionFactory();
      try (JMSContext jmsContext = connectionFactory.createContext()) {
         jmsContext.createSharedDurableConsumer(jmsContext.createTopic("config_test_consumer_created_queues"),"mySub").receive(100);
      }

      try {
         Assert.assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_consumer_created_queues").contains("mySub"));

         Assert.assertNotNull(getAddressInfo(embeddedActiveMQ, "config_test_address_removal_no_queue"));
         Assert.assertNotNull(getAddressInfo(embeddedActiveMQ, "config_test_address_removal"));
         Assert.assertNotNull(getAddressInfo(embeddedActiveMQ, "config_test_queue_removal"));
         Assert.assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_queue_removal").contains("config_test_queue_removal_queue_1"));
         Assert.assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_queue_removal").contains("config_test_queue_removal_queue_2"));

         Assert.assertNotNull(getAddressInfo(embeddedActiveMQ, "permanent_test_address_removal"));
         Assert.assertNotNull(getAddressInfo(embeddedActiveMQ, "permanent_test_queue_removal"));
         Assert.assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "permanent_test_queue_removal").contains("permanent_test_queue_removal_queue_1"));
         Assert.assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "permanent_test_queue_removal").contains("permanent_test_queue_removal_queue_2"));

         Assert.assertNotNull(getAddressInfo(embeddedActiveMQ, "config_test_queue_change"));
         Assert.assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_queue_change").contains("config_test_queue_change_queue"));
         Assert.assertEquals(10, getQueue(embeddedActiveMQ, "config_test_queue_change_queue").getMaxConsumers());
         Assert.assertFalse(getQueue(embeddedActiveMQ, "config_test_queue_change_queue").isPurgeOnNoConsumers());

         Files.copy(url2.openStream(), brokerXML, StandardCopyOption.REPLACE_EXISTING);
         brokerXML.toFile().setLastModified(System.currentTimeMillis() + 1000);

         embeddedActiveMQ.getActiveMQServer().reloadConfigurationFile();

         //Ensure queues created by clients (NOT by broker.xml are not removed when we reload).
         Assert.assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_consumer_created_queues").contains("mySub"));

         Assert.assertNull(getAddressInfo(embeddedActiveMQ, "config_test_address_removal_no_queue"));
         Assert.assertNull(getAddressInfo(embeddedActiveMQ, "config_test_address_removal"));
         Assert.assertNotNull(getAddressInfo(embeddedActiveMQ, "config_test_queue_removal"));
         Assert.assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_queue_removal").contains("config_test_queue_removal_queue_1"));
         Assert.assertFalse(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_queue_removal").contains("config_test_queue_removal_queue_2"));

         Assert.assertNotNull(getAddressInfo(embeddedActiveMQ, "permanent_test_address_removal"));
         Assert.assertNotNull(getAddressInfo(embeddedActiveMQ, "permanent_test_queue_removal"));
         Assert.assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "permanent_test_queue_removal").contains("permanent_test_queue_removal_queue_1"));
         Assert.assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "permanent_test_queue_removal").contains("permanent_test_queue_removal_queue_2"));

         Assert.assertNotNull(getAddressInfo(embeddedActiveMQ, "config_test_queue_change"));
         Assert.assertTrue(listQueuesNamesForAddress(embeddedActiveMQ, "config_test_queue_change").contains("config_test_queue_change_queue"));
         Assert.assertEquals(1, getQueue(embeddedActiveMQ, "config_test_queue_change_queue").getMaxConsumers());
         Assert.assertTrue(getQueue(embeddedActiveMQ, "config_test_queue_change_queue").isPurgeOnNoConsumers());

         Assert.assertNull(getAddressInfo(embeddedActiveMQ, "config_test_queue_change_queue"));
         Assert.assertNull(getAddressInfo(embeddedActiveMQ, "config_test_queue_removal_queue_1"));
      } finally {
         embeddedActiveMQ.stop();
      }
   }

   private AddressInfo getAddressInfo(EmbeddedActiveMQ embeddedActiveMQ, String address) {
      return embeddedActiveMQ.getActiveMQServer().getPostOffice().getAddressInfo(SimpleString.toSimpleString(address));
   }

   private org.apache.activemq.artemis.core.server.Queue getQueue(EmbeddedActiveMQ embeddedActiveMQ, String queueName) throws Exception {
      QueueBinding queueBinding = (QueueBinding) embeddedActiveMQ.getActiveMQServer().getPostOffice().getBinding(SimpleString.toSimpleString(queueName));
      return queueBinding == null ? null : queueBinding.getQueue();
   }

   private List<String> listQueuesNamesForAddress(EmbeddedActiveMQ embeddedActiveMQ, String address) throws Exception {
      return embeddedActiveMQ.getActiveMQServer().getPostOffice().listQueuesForAddress(SimpleString.toSimpleString(address)).stream().map(
          org.apache.activemq.artemis.core.server.Queue::getName).map(SimpleString::toString).collect(Collectors.toList());
   }

}
