/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.tests.integration.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.postoffice.impl.PostOfficeImpl;
import org.apache.activemq.artemis.core.postoffice.impl.PostOfficeTestAccessor;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.RandomUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.junit.Before;
import org.junit.Test;

public class AutoDeleteAddressTest extends ActiveMQTestBase {

   public final SimpleString addressA = new SimpleString("addressA");
   public final SimpleString queueA = new SimpleString("queueA");

   private ServerLocator locator;
   private ActiveMQServer server;
   private ClientSessionFactory cf;

   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();
      locator = createInVMNonHALocator();
      server = createServer(false);

      server.start();
      cf = createSessionFactory(locator);
   }

   @Test
   public void testAutoDeleteAutoCreatedAddress() throws Exception {
      // auto-delete-addresses defaults to true
      server.createQueue(new QueueConfiguration(queueA).setAddress(addressA).setRoutingType(RoutingType.ANYCAST).setAutoCreated(true));
      assertNotNull(server.getAddressInfo(addressA));
      cf.createSession().createConsumer(queueA).close();
      PostOfficeTestAccessor.sweepAndReapAddresses((PostOfficeImpl) server.getPostOffice());
      Wait.assertTrue(() -> server.getAddressInfo(addressA) == null);
   }

   @Test
   public void testNegativeAutoDeleteAutoCreatedAddress() throws Exception {
      server.getAddressSettingsRepository().addMatch(addressA.toString(), new AddressSettings().setAutoDeleteAddresses(false));
      server.createQueue(new QueueConfiguration(queueA).setAddress(addressA).setRoutingType(RoutingType.ANYCAST).setAutoCreated(true));
      assertNotNull(server.getAddressInfo(addressA));
      cf.createSession().createConsumer(queueA).close();
      PostOfficeTestAccessor.sweepAndReapAddresses((PostOfficeImpl) server.getPostOffice());
      assertNotNull(server.getAddressInfo(addressA));
   }

   @Test
   public void testAutoDeleteAddressWithWildcardAddress() throws Exception {
      String prefix = "topic";
      server.getAddressSettingsRepository().addMatch(prefix + ".#", new AddressSettings().setAutoDeleteAddresses(true).setAutoDeleteAddressesSkipUsageCheck(true));
      String wildcardAddress = prefix + ".#";
      String queue = RandomUtil.randomString();
      final int MESSAGE_COUNT = 10;
      final CountDownLatch latch = new CountDownLatch(MESSAGE_COUNT);

      server.createQueue(new QueueConfiguration(queue).setAddress(wildcardAddress).setRoutingType(RoutingType.ANYCAST).setAutoCreated(true));

      ClientSession consumerSession = cf.createSession();
      ClientConsumer consumer = consumerSession.createConsumer(queue);
      consumer.setMessageHandler(message -> latch.countDown());
      consumerSession.start();

      ClientSession producerSession = cf.createSession();
      ClientProducer producer = producerSession.createProducer();

      List<String> addresses = new ArrayList<>();
      for (int i = 0; i < MESSAGE_COUNT; i++) {
         String address = prefix + "." + RandomUtil.randomString();
         addresses.add(address);
         server.addAddressInfo(new AddressInfo(address).setAutoCreated(true));
         producer.send(address, producerSession.createMessage(false));
      }
      producerSession.close();

      assertTrue(latch.await(2, TimeUnit.SECONDS));

      for (String address : addresses) {
         assertNotNull(server.getAddressInfo(SimpleString.toSimpleString(address)));
         Wait.assertEquals(true, () -> Arrays.asList(server.getPagingManager().getStoreNames()).contains(SimpleString.toSimpleString(address)), 2000, 100);
      }

      PostOfficeTestAccessor.sweepAndReapAddresses((PostOfficeImpl) server.getPostOffice());

      for (String address : addresses) {
         assertNull(server.getAddressInfo(SimpleString.toSimpleString(address)));
         Wait.assertEquals(false, () -> Arrays.asList(server.getPagingManager().getStoreNames()).contains(SimpleString.toSimpleString(address)), 2000, 100);
      }

      consumerSession.close();
   }
}
