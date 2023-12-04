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
package org.apache.activemq.artemis.tests.integration.management;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;
import javax.transaction.xa.XAResource;
import javax.transaction.xa.Xid;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.activemq.artemis.api.config.ActiveMQDefaultConfiguration;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQIllegalStateException;
import org.apache.activemq.artemis.api.core.ActiveMQTimeoutException;
import org.apache.activemq.artemis.api.core.JsonUtil;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.api.core.management.ActiveMQServerControl;
import org.apache.activemq.artemis.api.core.management.AddressControl;
import org.apache.activemq.artemis.api.core.management.AddressSettingsInfo;
import org.apache.activemq.artemis.api.core.management.BridgeControl;
import org.apache.activemq.artemis.api.core.management.DivertControl;
import org.apache.activemq.artemis.api.core.management.ObjectNameBuilder;
import org.apache.activemq.artemis.api.core.management.QueueControl;
import org.apache.activemq.artemis.api.core.management.RoleInfo;
import org.apache.activemq.artemis.api.jms.ActiveMQJMSClient;
import org.apache.activemq.artemis.api.jms.JMSFactoryType;
import org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryImpl;
import org.apache.activemq.artemis.core.client.impl.ClientSessionImpl;
import org.apache.activemq.artemis.core.config.BridgeConfiguration;
import org.apache.activemq.artemis.core.config.ClusterConnectionConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.SecurityConfiguration;
import org.apache.activemq.artemis.core.management.impl.view.ConsumerField;
import org.apache.activemq.artemis.core.management.impl.view.ProducerField;
import org.apache.activemq.artemis.core.messagecounter.impl.MessageCounterManagerImpl;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.persistence.config.PersistedDivertConfiguration;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.TransportConstants;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.core.server.BrokerConnection;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerProducer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.ServiceComponent;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.server.impl.ServerLegacyProducersImpl;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerSessionPlugin;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.core.settings.impl.DeletionPolicy;
import org.apache.activemq.artemis.core.settings.impl.SlowConsumerPolicy;
import org.apache.activemq.artemis.core.transaction.impl.XidImpl;
import org.apache.activemq.artemis.jms.client.ActiveMQConnection;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.jms.client.ActiveMQSession;
import org.apache.activemq.artemis.json.JsonArray;
import org.apache.activemq.artemis.json.JsonObject;
import org.apache.activemq.artemis.json.JsonValue;
import org.apache.activemq.artemis.marker.WebServerComponentMarker;
import org.apache.activemq.artemis.nativo.jlibaio.LibaioContext;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.protocol.SessionCallback;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.spi.core.security.jaas.InVMLoginModule;
import org.apache.activemq.artemis.tests.unit.core.config.impl.fakes.FakeConnectorServiceFactory;
import org.apache.activemq.artemis.tests.util.CFUtil;
import org.apache.activemq.artemis.tests.util.Wait;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.artemis.utils.RetryMethod;
import org.apache.activemq.artemis.utils.RetryRule;
import org.apache.activemq.artemis.utils.UUIDGenerator;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RunWith(Parameterized.class)
public class ActiveMQServerControlTest extends ManagementTestBase {

   private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

   @Rule
   public RetryRule retryRule = new RetryRule(0);


   protected boolean legacyCreateQueue;

   // For any extended tests that may add extra prodcuers as part of the test
   protected int extraProducers = 0;

   private ActiveMQServer server;

   private Configuration conf;

   private TransportConfiguration connectorConfig;

   @Parameterized.Parameters(name = "legacyCreateQueue={0}")
   public static Collection<Object[]> getParams() {
      return Arrays.asList(new Object[][]{{true}, {false}});
   }

   public ActiveMQServerControlTest(boolean legacyCreateQueue) {
      this.legacyCreateQueue = legacyCreateQueue;
   }


   private static boolean contains(final String name, final String[] strings) {
      boolean found = false;
      for (String str : strings) {
         if (name.equals(str)) {
            found = true;
            break;
         }
      }
      return found;
   }



   public boolean usingCore() {
      return false;
   }

   @Test
   public void testGetAttributes() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();

      Assert.assertEquals(server.getConfiguration().getName(), serverControl.getName());

      Assert.assertEquals(server.getVersion().getFullVersion(), serverControl.getVersion());

      Assert.assertEquals(conf.isClustered(), serverControl.isClustered());
      Assert.assertEquals(conf.isPersistDeliveryCountBeforeDelivery(), serverControl.isPersistDeliveryCountBeforeDelivery());
      Assert.assertEquals(conf.getScheduledThreadPoolMaxSize(), serverControl.getScheduledThreadPoolMaxSize());
      Assert.assertEquals(conf.getThreadPoolMaxSize(), serverControl.getThreadPoolMaxSize());
      Assert.assertEquals(conf.getSecurityInvalidationInterval(), serverControl.getSecurityInvalidationInterval());
      Assert.assertEquals(conf.isSecurityEnabled(), serverControl.isSecurityEnabled());
      Assert.assertEquals(conf.isAsyncConnectionExecutionEnabled(), serverControl.isAsyncConnectionExecutionEnabled());
      Assert.assertEquals(conf.getIncomingInterceptorClassNames().size(), serverControl.getIncomingInterceptorClassNames().length);
      Assert.assertEquals(conf.getIncomingInterceptorClassNames().size(), serverControl.getIncomingInterceptorClassNames().length);
      Assert.assertEquals(conf.getOutgoingInterceptorClassNames().size(), serverControl.getOutgoingInterceptorClassNames().length);
      Assert.assertEquals(conf.getConnectionTTLOverride(), serverControl.getConnectionTTLOverride());
      //Assert.assertEquals(conf.getBackupConnectorName(), serverControl.getBackupConnectorName());
      Assert.assertEquals(conf.getManagementAddress().toString(), serverControl.getManagementAddress());
      Assert.assertEquals(conf.getManagementNotificationAddress().toString(), serverControl.getManagementNotificationAddress());
      Assert.assertEquals(conf.getIDCacheSize(), serverControl.getIDCacheSize());
      Assert.assertEquals(conf.isPersistIDCache(), serverControl.isPersistIDCache());
      Assert.assertEquals(conf.getBindingsDirectory(), serverControl.getBindingsDirectory());
      Assert.assertEquals(conf.getJournalDirectory(), serverControl.getJournalDirectory());
      Assert.assertEquals(conf.getJournalType().toString(), serverControl.getJournalType());
      Assert.assertEquals(conf.isJournalSyncTransactional(), serverControl.isJournalSyncTransactional());
      Assert.assertEquals(conf.isJournalSyncNonTransactional(), serverControl.isJournalSyncNonTransactional());
      Assert.assertEquals(conf.getJournalFileSize(), serverControl.getJournalFileSize());
      Assert.assertEquals(conf.getJournalMinFiles(), serverControl.getJournalMinFiles());
      if (LibaioContext.isLoaded()) {
         Assert.assertEquals(conf.getJournalMaxIO_AIO(), serverControl.getJournalMaxIO());
         Assert.assertEquals(conf.getJournalBufferSize_AIO(), serverControl.getJournalBufferSize());
         Assert.assertEquals(conf.getJournalBufferTimeout_AIO(), serverControl.getJournalBufferTimeout());
      }
      Assert.assertEquals(conf.isCreateBindingsDir(), serverControl.isCreateBindingsDir());
      Assert.assertEquals(conf.isCreateJournalDir(), serverControl.isCreateJournalDir());
      Assert.assertEquals(conf.getPagingDirectory(), serverControl.getPagingDirectory());
      Assert.assertEquals(conf.getLargeMessagesDirectory(), serverControl.getLargeMessagesDirectory());
      Assert.assertEquals(conf.isWildcardRoutingEnabled(), serverControl.isWildcardRoutingEnabled());
      Assert.assertEquals(conf.getTransactionTimeout(), serverControl.getTransactionTimeout());
      Assert.assertEquals(conf.isMessageCounterEnabled(), serverControl.isMessageCounterEnabled());
      Assert.assertEquals(conf.getTransactionTimeoutScanPeriod(), serverControl.getTransactionTimeoutScanPeriod());
      Assert.assertEquals(conf.getMessageExpiryScanPeriod(), serverControl.getMessageExpiryScanPeriod());
      Assert.assertEquals(conf.getJournalCompactMinFiles(), serverControl.getJournalCompactMinFiles());
      Assert.assertEquals(conf.getJournalCompactPercentage(), serverControl.getJournalCompactPercentage());
      Assert.assertEquals(conf.isPersistenceEnabled(), serverControl.isPersistenceEnabled());
      Assert.assertEquals(conf.getJournalPoolFiles(), serverControl.getJournalPoolFiles());
      Assert.assertTrue(serverControl.isActive());
   }

   @Test
   public void testSecurityCacheSizes() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();

      Wait.assertEquals(usingCore() ? 1 : 0, serverControl::getAuthenticationCacheSize);
      Wait.assertEquals(usingCore() ? 7 : 0, serverControl::getAuthorizationCacheSize);

      ServerLocator loc = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(loc);
      ClientSession session = csf.createSession("myUser", "myPass", false, true, false, false, 0);
      session.start();

      final String address = "ADDRESS";

      serverControl.createAddress(address, "MULTICAST");

      ClientProducer producer = session.createProducer(address);

      ClientMessage m = session.createMessage(true);
      m.putStringProperty("hello", "world");
      producer.send(m);

      Assert.assertEquals(usingCore() ? 2 : 1, serverControl.getAuthenticationCacheSize());
      Wait.assertEquals(usingCore() ? 8 : 1, () -> serverControl.getAuthorizationCacheSize());
   }

   @Test
   public void testCurrentTime() throws Exception {
      long time = System.currentTimeMillis();
      ActiveMQServerControl serverControl = createManagementControl();
      Assert.assertTrue("serverControl returned an invalid time.", serverControl.getCurrentTimeMillis() >= time);
   }

   @Test
   public void testClearingSecurityCaches() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator loc = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(loc);
      ClientSession session = csf.createSession("myUser", "myPass", false, true, false, false, 0);
      session.start();

      final String address = "ADDRESS";
      serverControl.createAddress(address, "MULTICAST");
      ClientProducer producer = session.createProducer(address);
      ClientMessage m = session.createMessage(true);
      m.putStringProperty("hello", "world");
      producer.send(m);

      Assert.assertTrue(serverControl.getAuthenticationCacheSize() > 0);
      Wait.assertTrue(() -> serverControl.getAuthorizationCacheSize() > 0);

      serverControl.clearAuthenticationCache();
      serverControl.clearAuthorizationCache();

      Assert.assertEquals(usingCore() ? 1 : 0, serverControl.getAuthenticationCacheSize());
      Assert.assertEquals(usingCore() ? 7 : 0, serverControl.getAuthorizationCacheSize());
   }

   @Test
   public void testGetConnectors() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();

      Object[] connectorData = serverControl.getConnectors();
      Assert.assertNotNull(connectorData);
      Assert.assertEquals(1, connectorData.length);

      Object[] config = (Object[]) connectorData[0];

      Assert.assertEquals(connectorConfig.getName(), config[0]);
   }

   @Test
   public void testGetAcceptors() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();

      Object[] acceptors = serverControl.getAcceptors();
      Assert.assertNotNull(acceptors);
      Assert.assertEquals(2, acceptors.length);

      for (int i = 0; i < acceptors.length; i++) {
         Object[] acceptor = (Object[]) acceptors[i];
         String name = (String) acceptor[0];
         assertTrue(name.equals("netty") || name.equals("invm"));
      }
   }

   @Test
   public void testIsReplicaSync() throws Exception {
      Assert.assertFalse(createManagementControl().isReplicaSync());
   }

   @Test
   public void testGetConnectorsAsJSON() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();

      String jsonString = serverControl.getConnectorsAsJSON();
      Assert.assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      Assert.assertEquals(1, array.size());
      JsonObject data = array.getJsonObject(0);
      Assert.assertEquals(connectorConfig.getName(), data.getString("name"));
      Assert.assertEquals(connectorConfig.getFactoryClassName(), data.getString("factoryClassName"));
      Assert.assertEquals(connectorConfig.getParams().size(), data.getJsonObject("params").size());
   }

   @Test
   public void testGetAcceptorsAsJSON() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();

      String jsonString = serverControl.getAcceptorsAsJSON();
      Assert.assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      Assert.assertEquals(2, array.size());
      for (int i = 0; i < array.size(); i++) {
         String name = ((JsonObject)array.get(i)).getString("name");
         assertTrue(name.equals("netty") || name.equals("invm"));
      }
   }

   @Test
   public void testCreateAndDestroyQueue() throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString name = RandomUtil.randomSimpleString();

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      serverControl.createAddress(address.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), null, true, -1, false, false);
      } else {
         serverControl.createQueue(new QueueConfiguration(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setAutoCreateAddress(false).toJSON());
      }

      checkResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      Assert.assertEquals(address.toString(), queueControl.getAddress());
      Assert.assertEquals(name.toString(), queueControl.getName());
      Assert.assertNull(queueControl.getFilter());
      Assert.assertEquals(true, queueControl.isDurable());
      Assert.assertEquals(false, queueControl.isTemporary());

      serverControl.destroyQueue(name.toString());

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
   }

   @Test
   public void testCreateQueueWithNullAddress() throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString name = address;

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         serverControl.createQueue(null, name.toString(), "ANYCAST");
      } else {
         serverControl.createQueue(new QueueConfiguration(name).setRoutingType(RoutingType.ANYCAST).toJSON());
      }

      checkResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(name, name, RoutingType.ANYCAST));
      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      Assert.assertEquals(address.toString(), queueControl.getAddress());
      Assert.assertEquals(name.toString(), queueControl.getName());
      Assert.assertNull(queueControl.getFilter());
      Assert.assertEquals(true, queueControl.isDurable());
      Assert.assertEquals(false, queueControl.isTemporary());

      serverControl.destroyQueue(name.toString());

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
   }

   @Test
   public void testCreateAndDestroyQueue_2() throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString name = RandomUtil.randomSimpleString();
      String filter = "color = 'green'";
      boolean durable = true;

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      serverControl.createAddress(address.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), filter, durable, -1, false, false);
      } else {
         serverControl.createQueue(new QueueConfiguration(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setFilterString(filter).setAutoCreateAddress(false).toJSON());
      }

      checkResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      Assert.assertEquals(address.toString(), queueControl.getAddress());
      Assert.assertEquals(name.toString(), queueControl.getName());
      Assert.assertEquals(filter, queueControl.getFilter());
      Assert.assertEquals(durable, queueControl.isDurable());
      Assert.assertEquals(false, queueControl.isTemporary());

      serverControl.destroyQueue(name.toString());

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
   }

   @Test
   public void testCreateAndDestroyQueue_3() throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString name = RandomUtil.randomSimpleString();
      boolean durable = true;

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      serverControl.createAddress(address.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), null, durable, -1, false, false);
      } else {
         serverControl.createQueue(new QueueConfiguration(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(durable).setAutoCreateAddress(false).toJSON());
      }

      checkResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      Assert.assertEquals(address.toString(), queueControl.getAddress());
      Assert.assertEquals(name.toString(), queueControl.getName());
      Assert.assertNull(queueControl.getFilter());
      Assert.assertEquals(durable, queueControl.isDurable());
      Assert.assertEquals(false, queueControl.isTemporary());

      serverControl.destroyQueue(name.toString());

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
   }

   @Test
   public void testCreateAndDestroyQueue_4() throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString name = RandomUtil.randomSimpleString();
      boolean durable = RandomUtil.randomBoolean();
      boolean purgeOnNoConsumers = RandomUtil.randomBoolean();
      boolean autoCreateAddress = true;
      int maxConsumers = RandomUtil.randomInt();

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), RoutingType.ANYCAST.toString(), name.toString(), null, durable, maxConsumers, purgeOnNoConsumers, autoCreateAddress);
      } else {
         serverControl.createQueue(new QueueConfiguration(name)
                                      .setAddress(address)
                                      .setRoutingType(RoutingType.ANYCAST)
                                      .setDurable(durable)
                                      .setMaxConsumers(maxConsumers)
                                      .setPurgeOnNoConsumers(purgeOnNoConsumers)
                                      .setAutoCreateAddress(autoCreateAddress)
                                      .toJSON());
      }

      checkResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      Assert.assertEquals(address.toString(), queueControl.getAddress());
      Assert.assertEquals(name.toString(), queueControl.getName());
      Assert.assertNull(queueControl.getFilter());
      Assert.assertEquals(durable, queueControl.isDurable());
      Assert.assertEquals(purgeOnNoConsumers, queueControl.isPurgeOnNoConsumers());
      Assert.assertEquals(maxConsumers, queueControl.getMaxConsumers());
      Assert.assertEquals(false, queueControl.isTemporary());

      checkResource(ObjectNameBuilder.DEFAULT.getAddressObjectName(address));
      AddressControl addressControl = ManagementControlHelper.createAddressControl(address, mbeanServer);
      Assert.assertEquals(address.toString(), addressControl.getAddress());

      serverControl.destroyQueue(name.toString(), true, true);

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      checkNoResource(ObjectNameBuilder.DEFAULT.getAddressObjectName(address));
   }

   @Test
   public void testCreateAndDestroyQueue_5() throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString name = RandomUtil.randomSimpleString();
      boolean durable = RandomUtil.randomBoolean();
      int maxConsumers = RandomUtil.randomInt();
      boolean purgeOnNoConsumers = RandomUtil.randomBoolean();
      boolean exclusive = RandomUtil.randomBoolean();
      boolean groupRebalance = RandomUtil.randomBoolean();
      int groupBuckets = 1;
      String groupFirstKey = RandomUtil.randomSimpleString().toString();
      boolean lastValue = false;
      String lastValueKey = null;
      boolean nonDestructive = RandomUtil.randomBoolean();
      int consumersBeforeDispatch = RandomUtil.randomInt();
      long delayBeforeDispatch = RandomUtil.randomLong();
      boolean autoDelete = RandomUtil.randomBoolean();
      long autoDeleteDelay = RandomUtil.randomLong();
      long autoDeleteMessageCount = RandomUtil.randomLong();
      boolean autoCreateAddress = true;
      long ringSize = RandomUtil.randomLong();

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), RoutingType.ANYCAST.toString(), name.toString(), null, durable, maxConsumers, purgeOnNoConsumers, exclusive, groupRebalance, groupBuckets, groupFirstKey, lastValue, lastValueKey, nonDestructive, consumersBeforeDispatch, delayBeforeDispatch, autoDelete, autoDeleteDelay, autoDeleteMessageCount, autoCreateAddress, ringSize);
      } else {
         serverControl.createQueue(new QueueConfiguration(name)
                                      .setAddress(address)
                                      .setRoutingType(RoutingType.ANYCAST)
                                      .setDurable(durable)
                                      .setMaxConsumers(maxConsumers)
                                      .setPurgeOnNoConsumers(purgeOnNoConsumers)
                                      .setExclusive(exclusive)
                                      .setGroupRebalance(groupRebalance)
                                      .setGroupBuckets(groupBuckets)
                                      .setGroupFirstKey(groupFirstKey)
                                      .setLastValue(lastValue)
                                      .setLastValueKey(lastValueKey)
                                      .setNonDestructive(nonDestructive)
                                      .setConsumersBeforeDispatch(consumersBeforeDispatch)
                                      .setDelayBeforeDispatch(delayBeforeDispatch)
                                      .setAutoDelete(autoDelete)
                                      .setAutoDeleteDelay(autoDeleteDelay)
                                      .setAutoDeleteMessageCount(autoDeleteMessageCount)
                                      .setRingSize(ringSize)
                                      .setAutoCreateAddress(autoCreateAddress)
                                      .toJSON());
      }

      checkResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      Assert.assertEquals(address.toString(), queueControl.getAddress());
      Assert.assertEquals(name.toString(), queueControl.getName());
      Assert.assertNull(queueControl.getFilter());
      Assert.assertEquals(durable, queueControl.isDurable());
      Assert.assertEquals(maxConsumers, queueControl.getMaxConsumers());
      Assert.assertEquals(purgeOnNoConsumers, queueControl.isPurgeOnNoConsumers());
      Assert.assertEquals(false, queueControl.isTemporary());
      Assert.assertEquals(exclusive, queueControl.isExclusive());
      Assert.assertEquals(groupRebalance, queueControl.isGroupRebalance());
      Assert.assertEquals(groupBuckets, queueControl.getGroupBuckets());
      Assert.assertEquals(groupFirstKey, queueControl.getGroupFirstKey());
      Assert.assertEquals(lastValue, queueControl.isLastValue());
//      Assert.assertEquals(lastValueKey, queueControl.getLastValueKey());
//      Assert.assertEquals(nonDestructive, queueControl.isNonDestructive());
//      Assert.assertEquals(consumersBeforeDispatch, queueControl.getConsumersBeforeDispatch());
//      Assert.assertEquals(delayBeforeDispatch, queueControl.getDelayBeforeDispatch());
//      Assert.assertEquals(autoDelete, queueControl.isAutoDelete());
//      Assert.assertEquals(autoDeleteDelay, queueControl.getAutoDeleteDelay());
//      Assert.assertEquals(autoDeleteMessageCount, queueControl.autoDeleteMessageCount());
      Assert.assertEquals(ringSize, queueControl.getRingSize());

      checkResource(ObjectNameBuilder.DEFAULT.getAddressObjectName(address));
      AddressControl addressControl = ManagementControlHelper.createAddressControl(address, mbeanServer);
      Assert.assertEquals(address.toString(), addressControl.getAddress());

      serverControl.destroyQueue(name.toString(), true, true);

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      checkNoResource(ObjectNameBuilder.DEFAULT.getAddressObjectName(address));
   }

   @Test
   public void testCreateAndDestroyQueueWithAutoDeleteAddress() throws Exception {
      server.getAddressSettingsRepository().addMatch("#", new AddressSettings().setAutoDeleteAddresses(false));
      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString name = RandomUtil.randomSimpleString();

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), null, true, -1, false, true);
      } else {
         serverControl.createQueue(new QueueConfiguration(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setAutoCreateAddress(true).toJSON());
      }

      checkResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      Assert.assertEquals(address.toString(), queueControl.getAddress());
      Assert.assertEquals(name.toString(), queueControl.getName());
      Assert.assertNull(queueControl.getFilter());
      Assert.assertEquals(true, queueControl.isDurable());
      Assert.assertEquals(false, queueControl.isTemporary());

      serverControl.destroyQueue(name.toString(), false, true);

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      checkNoResource(ObjectNameBuilder.DEFAULT.getAddressObjectName(address));
   }

   @Test
   public void testRemoveQueueFilter() throws Exception {

      String address = RandomUtil.randomString();
      QueueConfiguration queue1 = new QueueConfiguration("q1")
              .setAddress(address)
              .setFilterString("hello='world'");

      QueueConfiguration queue2 = new QueueConfiguration("q2")
              .setAddress(address)
              .setFilterString("hello='darling'");

      ActiveMQServerControl serverControl = createManagementControl();
      serverControl.createAddress(address, "MULTICAST");

      if (legacyCreateQueue) {
         serverControl.createQueue(address, queue1.getName().toString(), queue1.getFilterString().toString(), queue1.isDurable());
         serverControl.createQueue(address, queue2.getName().toString(), queue2.getFilterString().toString(), queue2.isDurable());
      } else {
         serverControl.createQueue(queue1.toJSON());
         serverControl.createQueue(queue2.toJSON());
      }

      ServerLocator loc = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(loc);
      ClientSession session = csf.createSession();
      session.start();

      ClientProducer producer = session.createProducer(address);
      ClientConsumer consumer1 = session.createConsumer("q1");
      ClientConsumer consumer2 = session.createConsumer("q2");

      ClientMessage m = session.createMessage(true);
      m.putStringProperty("hello", "world");
      producer.send(m);

      assertNotNull(consumer1.receiveImmediate());
      assertNull(consumer2.receiveImmediate());

      serverControl.updateQueue(queue2.setFilterString((String) null).toJSON());

      producer.send(m);

      assertNotNull(consumer1.receive(1000));
      assertNotNull(consumer2.receive(1000));
   }

   @Test
   public void testCreateAndDestroyQueueClosingConsumers() throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString name = RandomUtil.randomSimpleString();
      boolean durable = true;

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      serverControl.createAddress(address.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), null, durable, -1, false, false);
      } else {
         serverControl.createQueue(new QueueConfiguration(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(durable).setAutoCreateAddress(false).toJSON());
      }

      ServerLocator receiveLocator = createInVMNonHALocator();
      ClientSessionFactory receiveCsf = createSessionFactory(receiveLocator);
      ClientSession receiveClientSession = receiveCsf.createSession(true, false, false);
      final ClientConsumer consumer = receiveClientSession.createConsumer(name);

      Assert.assertFalse(consumer.isClosed());

      checkResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      serverControl.destroyQueue(name.toString(), true);
      Wait.waitFor(new Wait.Condition() {
         @Override
         public boolean isSatisfied() throws Exception {
            return consumer.isClosed();
         }
      }, 1000, 100);
      Assert.assertTrue(consumer.isClosed());

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
   }

   @Test
   public void testCreateAndDestroyQueueWithNullFilter() throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString name = RandomUtil.randomSimpleString();
      String filter = null;
      boolean durable = true;

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      serverControl.createAddress(address.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), filter, durable, -1, false, false);
      } else {
         serverControl.createQueue(new QueueConfiguration(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(durable).setAutoCreateAddress(false).toJSON());
      }

      checkResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      Assert.assertEquals(address.toString(), queueControl.getAddress());
      Assert.assertEquals(name.toString(), queueControl.getName());
      Assert.assertNull(queueControl.getFilter());
      Assert.assertEquals(durable, queueControl.isDurable());
      Assert.assertEquals(false, queueControl.isTemporary());

      serverControl.destroyQueue(name.toString());

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
   }

   @Test
   public void testCreateAndDestroyQueueWithEmptyStringForFilter() throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString name = RandomUtil.randomSimpleString();
      String filter = "";
      boolean durable = true;

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      serverControl.createAddress(address.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), filter, durable, -1, false, false);
      } else {
         serverControl.createQueue(new QueueConfiguration(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setFilterString(filter).setDurable(durable).setAutoCreateAddress(false).toJSON());
      }

      checkResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      Assert.assertEquals(address.toString(), queueControl.getAddress());
      Assert.assertEquals(name.toString(), queueControl.getName());
      Assert.assertNull(queueControl.getFilter());
      Assert.assertEquals(durable, queueControl.isDurable());
      Assert.assertEquals(false, queueControl.isTemporary());

      serverControl.destroyQueue(name.toString());

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
   }

   @Test
   public void testCreateAndUpdateQueueWithoutFilter() throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString name = RandomUtil.randomSimpleString();

      ActiveMQServerControl serverControl = createManagementControl();

      serverControl.createQueue(new QueueConfiguration(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setAutoCreateAddress(true).setFilterString((String) null).toJSON());
      serverControl.updateQueue(new QueueConfiguration(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setMaxConsumers(1).toJSON());

      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      Assert.assertEquals(address.toString(), queueControl.getAddress());
      Assert.assertEquals(name.toString(), queueControl.getName());
      Assert.assertNull(queueControl.getFilter());
      Assert.assertEquals(1, queueControl.getMaxConsumers());

      serverControl.destroyQueue(name.toString());
   }

   @Test
   public void testCreateAndLegacyUpdateQueueRingSize() throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString name = RandomUtil.randomSimpleString();

      ActiveMQServerControl serverControl = createManagementControl();

      serverControl.createQueue(new QueueConfiguration(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setAutoCreateAddress(true).toJSON());
      serverControl.updateQueue(name.toString(),
                                RoutingType.ANYCAST.toString(),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                101L);

      QueueControl queueControl = ManagementControlHelper.createQueueControl(address, name, RoutingType.ANYCAST, mbeanServer);
      Assert.assertEquals(address.toString(), queueControl.getAddress());
      Assert.assertEquals(name.toString(), queueControl.getName());
      Assert.assertEquals(101, queueControl.getRingSize());

      serverControl.destroyQueue(name.toString());
   }

   @Test
   public void testGetQueueCount() throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString name = RandomUtil.randomSimpleString();

      ActiveMQServerControl serverControl = createManagementControl();

      // due to replication, there can be another queue created for replicating
      // management operations
      Assert.assertFalse(ActiveMQServerControlTest.contains(name.toString(), serverControl.getQueueNames()));

      int countBeforeCreate = serverControl.getQueueCount();

      serverControl.createAddress(address.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), null, true, -1, false, false);
      } else {
         serverControl.createQueue(new QueueConfiguration(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(true).setAutoCreateAddress(false).toJSON());
      }

      Assert.assertTrue(countBeforeCreate < serverControl.getQueueCount());

      serverControl.destroyQueue(name.toString());
      Assert.assertFalse(ActiveMQServerControlTest.contains(name.toString(), serverControl.getQueueNames()));
   }

   @Test
   public void testGetQueueNames() throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString name = RandomUtil.randomSimpleString();

      ActiveMQServerControl serverControl = createManagementControl();

      // due to replication, there can be another queue created for replicating
      // management operations

      Assert.assertFalse(ActiveMQServerControlTest.contains(name.toString(), serverControl.getQueueNames()));
      serverControl.createAddress(address.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), null, true, -1, false, false);
      } else {
         serverControl.createQueue(new QueueConfiguration(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(true).setAutoCreateAddress(false).toJSON());
      }
      Assert.assertTrue(ActiveMQServerControlTest.contains(name.toString(), serverControl.getQueueNames()));

      serverControl.destroyQueue(name.toString());
      Assert.assertFalse(ActiveMQServerControlTest.contains(name.toString(), serverControl.getQueueNames()));
   }

   @Test
   public void testGetQueueNamesWithRoutingType() throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString anycastName = RandomUtil.randomSimpleString();
      SimpleString multicastName = RandomUtil.randomSimpleString();

      ActiveMQServerControl serverControl = createManagementControl();

      // due to replication, there can be another queue created for replicating
      // management operations

      Assert.assertFalse(ActiveMQServerControlTest.contains(anycastName.toString(), serverControl.getQueueNames()));
      Assert.assertFalse(ActiveMQServerControlTest.contains(multicastName.toString(), serverControl.getQueueNames()));

      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), RoutingType.ANYCAST.toString(), anycastName.toString(), null, true, -1, false, true);
      } else {
         serverControl.createQueue(new QueueConfiguration(anycastName).setAddress(address).setRoutingType(RoutingType.ANYCAST).toJSON());
      }
      Assert.assertTrue(ActiveMQServerControlTest.contains(anycastName.toString(), serverControl.getQueueNames(RoutingType.ANYCAST.toString())));
      Assert.assertFalse(ActiveMQServerControlTest.contains(anycastName.toString(), serverControl.getQueueNames(RoutingType.MULTICAST.toString())));

      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), RoutingType.MULTICAST.toString(), multicastName.toString(), null, true, -1, false, true);
      } else {
         serverControl.createQueue(new QueueConfiguration(multicastName).setAddress(address).setRoutingType(RoutingType.MULTICAST).toJSON());
      }
      Assert.assertTrue(ActiveMQServerControlTest.contains(multicastName.toString(), serverControl.getQueueNames(RoutingType.MULTICAST.toString())));
      Assert.assertFalse(ActiveMQServerControlTest.contains(multicastName.toString(), serverControl.getQueueNames(RoutingType.ANYCAST.toString())));

      serverControl.destroyQueue(anycastName.toString());
      serverControl.destroyQueue(multicastName.toString());
      Assert.assertFalse(ActiveMQServerControlTest.contains(anycastName.toString(), serverControl.getQueueNames()));
      Assert.assertFalse(ActiveMQServerControlTest.contains(multicastName.toString(), serverControl.getQueueNames()));
   }

   @Test
   public void testGetClusterConnectionNames() throws Exception {
      String clusterConnection1 = RandomUtil.randomString();
      String clusterConnection2 = RandomUtil.randomString();

      ActiveMQServerControl serverControl = createManagementControl();

      Assert.assertFalse(ActiveMQServerControlTest.contains(clusterConnection1, serverControl.getClusterConnectionNames()));
      Assert.assertFalse(ActiveMQServerControlTest.contains(clusterConnection2, serverControl.getClusterConnectionNames()));

      server.stop();
      server
         .getConfiguration()
         .addClusterConfiguration(new ClusterConnectionConfiguration().setName(clusterConnection1).setConnectorName(connectorConfig.getName()))
         .addClusterConfiguration(new ClusterConnectionConfiguration().setName(clusterConnection2).setConnectorName(connectorConfig.getName()));
      server.start();

      Assert.assertTrue(ActiveMQServerControlTest.contains(clusterConnection1, serverControl.getClusterConnectionNames()));
      Assert.assertTrue(ActiveMQServerControlTest.contains(clusterConnection2, serverControl.getClusterConnectionNames()));
   }

   @Test
   public void testGetAddressCount() throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString name = RandomUtil.randomSimpleString();

      ActiveMQServerControl serverControl = createManagementControl();

      // due to replication, there can be another queue created for replicating
      // management operations
      Assert.assertFalse(ActiveMQServerControlTest.contains(address.toString(), serverControl.getAddressNames()));

      int countBeforeCreate = serverControl.getAddressCount();

      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), null, true, -1, false, true);
      } else {
         serverControl.createQueue(new QueueConfiguration(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).toJSON());
      }

      Assert.assertTrue(countBeforeCreate < serverControl.getAddressCount());

      serverControl.destroyQueue(name.toString(), true, true);
      Wait.assertFalse(() -> ActiveMQServerControlTest.contains(address.toString(), serverControl.getAddressNames()));
   }

   @Test
   public void testGetAddressNames() throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString name = RandomUtil.randomSimpleString();

      ActiveMQServerControl serverControl = createManagementControl();

      // due to replication, there can be another queue created for replicating
      // management operations

      Assert.assertFalse(ActiveMQServerControlTest.contains(address.toString(), serverControl.getAddressNames()));
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), null, true, -1, false, true);
      } else {
         serverControl.createQueue(new QueueConfiguration(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).toJSON());
      }
      Assert.assertTrue(ActiveMQServerControlTest.contains(address.toString(), serverControl.getAddressNames()));

      serverControl.destroyQueue(name.toString(), true, true);
      Wait.assertFalse(() -> ActiveMQServerControlTest.contains(address.toString(), serverControl.getAddressNames()));
   }

   @Test
   public void testGetAddressDeletedFromJournal() throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString name = RandomUtil.randomSimpleString();

      ActiveMQServerControl serverControl = createManagementControl();

      // due to replication, there can be another queue created for replicating
      // management operations

      Assert.assertFalse(ActiveMQServerControlTest.contains(address.toString(), serverControl.getAddressNames()));
      serverControl.createAddress(address.toString(), "ANYCAST");
      Assert.assertTrue(ActiveMQServerControlTest.contains(address.toString(), serverControl.getAddressNames()));

      restartServer();

      serverControl.deleteAddress(address.toString());

      restartServer();

      Assert.assertFalse(ActiveMQServerControlTest.contains(address.toString(), serverControl.getAddressNames()));
   }

   @Test
   public void testMessageCounterMaxDayCount() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();

      Assert.assertEquals(MessageCounterManagerImpl.DEFAULT_MAX_DAY_COUNT, serverControl.getMessageCounterMaxDayCount());

      int newCount = 100;
      serverControl.setMessageCounterMaxDayCount(newCount);

      Assert.assertEquals(newCount, serverControl.getMessageCounterMaxDayCount());

      try {
         serverControl.setMessageCounterMaxDayCount(-1);
         Assert.fail();
      } catch (Exception e) {
      }

      try {
         serverControl.setMessageCounterMaxDayCount(0);
         Assert.fail();
      } catch (Exception e) {
      }

      Assert.assertEquals(newCount, serverControl.getMessageCounterMaxDayCount());
   }

   @Test
   public void testGetMessageCounterSamplePeriod() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();

      Assert.assertEquals(MessageCounterManagerImpl.DEFAULT_SAMPLE_PERIOD, serverControl.getMessageCounterSamplePeriod());

      long newSample = 20000;
      serverControl.setMessageCounterSamplePeriod(newSample);

      Assert.assertEquals(newSample, serverControl.getMessageCounterSamplePeriod());

      try {
         serverControl.setMessageCounterSamplePeriod(-1);
         Assert.fail();
      } catch (Exception e) {
      }

      try {
         serverControl.setMessageCounterSamplePeriod(0);
         Assert.fail();
      } catch (Exception e) {
      }

      //this only gets warning now and won't cause exception.
      serverControl.setMessageCounterSamplePeriod(MessageCounterManagerImpl.MIN_SAMPLE_PERIOD - 1);

      Assert.assertEquals(MessageCounterManagerImpl.MIN_SAMPLE_PERIOD - 1, serverControl.getMessageCounterSamplePeriod());
   }

   protected void restartServer() throws Exception {
      server.stop();
      server.start();
   }

   @Test
   public void testSecuritySettings() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      String addressMatch = "test.#";
      String exactAddress = "test.whatever";

      assertEquals(1, serverControl.getRoles(addressMatch).length);
      serverControl.addSecuritySettings(addressMatch, "foo", "foo, bar", null, "bar", "foo, bar", "", "", "bar", "foo", "foo");

      // Restart the server. Those settings should be persisted

      restartServer();

      serverControl = createManagementControl();

      String rolesAsJSON = serverControl.getRolesAsJSON(exactAddress);
      RoleInfo[] roleInfos = RoleInfo.from(rolesAsJSON);
      assertEquals(2, roleInfos.length);
      RoleInfo fooRoleInfo = null;
      RoleInfo barRoleInfo = null;
      if ("foo".equals(roleInfos[0].getName())) {
         fooRoleInfo = roleInfos[0];
         barRoleInfo = roleInfos[1];
      } else {
         fooRoleInfo = roleInfos[1];
         barRoleInfo = roleInfos[0];
      }
      assertTrue(fooRoleInfo.isSend());
      assertTrue(fooRoleInfo.isConsume());
      assertFalse(fooRoleInfo.isCreateDurableQueue());
      assertFalse(fooRoleInfo.isDeleteDurableQueue());
      assertTrue(fooRoleInfo.isCreateNonDurableQueue());
      assertFalse(fooRoleInfo.isDeleteNonDurableQueue());
      assertFalse(fooRoleInfo.isManage());
      assertFalse(fooRoleInfo.isBrowse());
      assertTrue(fooRoleInfo.isCreateAddress());
      assertTrue(fooRoleInfo.isDeleteAddress());

      assertFalse(barRoleInfo.isSend());
      assertTrue(barRoleInfo.isConsume());
      assertFalse(barRoleInfo.isCreateDurableQueue());
      assertTrue(barRoleInfo.isDeleteDurableQueue());
      assertTrue(barRoleInfo.isCreateNonDurableQueue());
      assertFalse(barRoleInfo.isDeleteNonDurableQueue());
      assertFalse(barRoleInfo.isManage());
      assertTrue(barRoleInfo.isBrowse());
      assertFalse(barRoleInfo.isCreateAddress());
      assertFalse(barRoleInfo.isDeleteAddress());

      Object[] roles = serverControl.getRoles(exactAddress);
      assertEquals(2, roles.length);
      Object[] fooRole = null;
      Object[] barRole = null;
      if ("foo".equals(((Object[])roles[0])[0])) {
         fooRole = (Object[]) roles[0];
         barRole = (Object[]) roles[1];
      } else {
         fooRole = (Object[]) roles[1];
         barRole = (Object[]) roles[0];
      }
      Assert.assertEquals(CheckType.values().length + 1, fooRole.length);
      Assert.assertEquals(CheckType.values().length + 1, barRole.length);

      assertTrue((boolean)fooRole[1]);
      assertTrue((boolean)fooRole[2]);
      assertFalse((boolean)fooRole[3]);
      assertFalse((boolean)fooRole[4]);
      assertTrue((boolean)fooRole[5]);
      assertFalse((boolean)fooRole[6]);
      assertFalse((boolean)fooRole[7]);
      assertFalse((boolean)fooRole[8]);
      assertTrue((boolean)fooRole[9]);
      assertTrue((boolean)fooRole[10]);

      assertFalse((boolean)barRole[1]);
      assertTrue((boolean)barRole[2]);
      assertFalse((boolean)barRole[3]);
      assertTrue((boolean)barRole[4]);
      assertTrue((boolean)barRole[5]);
      assertFalse((boolean)barRole[6]);
      assertFalse((boolean)barRole[7]);
      assertTrue((boolean)barRole[8]);
      assertFalse((boolean)barRole[9]);
      assertFalse((boolean)barRole[10]);

      serverControl.removeSecuritySettings(addressMatch);
      assertEquals(1, serverControl.getRoles(exactAddress).length);
   }

   @Test
   public void mergeAddressSettings() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setDeadLetterAddress(new SimpleString("DLA"));
      String returnedSettings = serverControl.addAddressSettings("foo", addressSettings.toJSON());
      AddressSettings info = AddressSettings.fromJSON(returnedSettings);
      assertEquals(addressSettings.getDeadLetterAddress(), info.getDeadLetterAddress());
      assertNull(info.getExpiryAddress());
      assertEquals(addressSettings.getRedeliveryDelay(), 0);


      addressSettings.setExpiryAddress(SimpleString.toSimpleString("EA"));
      returnedSettings = serverControl.addAddressSettings("foo", addressSettings.toJSON());
      info = AddressSettings.fromJSON(returnedSettings);
      assertEquals(addressSettings.getDeadLetterAddress(), info.getDeadLetterAddress());
      assertEquals(addressSettings.getExpiryAddress(), info.getExpiryAddress());

      addressSettings.setRedeliveryDelay(1000);
      returnedSettings = serverControl.addAddressSettings("foo", addressSettings.toJSON());
      info = AddressSettings.fromJSON(returnedSettings);
      assertEquals(addressSettings.getDeadLetterAddress(), info.getDeadLetterAddress());
      assertEquals(addressSettings.getExpiryAddress(), info.getExpiryAddress());
      assertEquals(addressSettings.getRedeliveryDelay(), info.getRedeliveryDelay());
   }
   @Test
   public void emptyAddressSettings() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      AddressSettings addressSettings = new AddressSettings();
      String returnedSettings = serverControl.addAddressSettings("foo", addressSettings.toJSON());
      AddressSettings info = AddressSettings.fromJSON(returnedSettings);

      assertEquals(addressSettings.getDeadLetterAddress(), info.getDeadLetterAddress());
      assertEquals(addressSettings.getExpiryAddress(), info.getExpiryAddress());
      assertEquals(addressSettings.getExpiryDelay(), info.getExpiryDelay());
      assertEquals(addressSettings.getMinExpiryDelay(), info.getMinExpiryDelay());
      assertEquals(addressSettings.getMaxExpiryDelay(), info.getMaxExpiryDelay());
      assertEquals(addressSettings.isDefaultLastValueQueue(), info.isDefaultLastValueQueue());
      assertEquals(addressSettings.getMaxDeliveryAttempts(), info.getMaxDeliveryAttempts());
      assertEquals(addressSettings.getMaxSizeBytes(), info.getMaxSizeBytes());
      assertEquals(addressSettings.getPageCacheMaxSize(), info.getPageCacheMaxSize());
      assertEquals(addressSettings.getPageSizeBytes(), info.getPageSizeBytes());
      assertEquals(addressSettings.getRedeliveryDelay(), info.getRedeliveryDelay());
      assertEquals(addressSettings.getRedeliveryMultiplier(), info.getRedeliveryMultiplier(), 0.000001);
      assertEquals(addressSettings.getMaxRedeliveryDelay(), info.getMaxRedeliveryDelay());
      assertEquals(addressSettings.getRedistributionDelay(), info.getRedistributionDelay());
      assertEquals(addressSettings.isSendToDLAOnNoRoute(), info.isSendToDLAOnNoRoute());
      assertEquals(addressSettings.getAddressFullMessagePolicy(), info.getAddressFullMessagePolicy());
      assertEquals(addressSettings.getSlowConsumerThreshold(), info.getSlowConsumerThreshold());
      assertEquals(addressSettings.getSlowConsumerCheckPeriod(), info.getSlowConsumerCheckPeriod());
      assertEquals(addressSettings.getSlowConsumerPolicy(), info.getSlowConsumerPolicy());
      assertEquals(addressSettings.isAutoCreateQueues(), info.isAutoCreateQueues());
      assertEquals(addressSettings.isAutoDeleteQueues(), info.isAutoDeleteQueues());
      assertEquals(addressSettings.isAutoCreateAddresses(), info.isAutoCreateAddresses());
      assertEquals(addressSettings.isAutoDeleteAddresses(), info.isAutoDeleteAddresses());
      assertEquals(addressSettings.getConfigDeleteQueues(), info.getConfigDeleteQueues());
      assertEquals(addressSettings.getConfigDeleteAddresses(), info.getConfigDeleteAddresses());
      assertEquals(addressSettings.getMaxSizeBytesRejectThreshold(), info.getMaxSizeBytesRejectThreshold());
      assertEquals(addressSettings.getDefaultLastValueKey(), info.getDefaultLastValueKey());
      assertEquals(addressSettings.isDefaultNonDestructive(), info.isDefaultNonDestructive());
      assertEquals(addressSettings.isDefaultExclusiveQueue(), info.isDefaultExclusiveQueue());
      assertEquals(addressSettings.isDefaultGroupRebalance(), info.isDefaultGroupRebalance());
      assertEquals(addressSettings.getDefaultGroupBuckets(), info.getDefaultGroupBuckets());
      assertEquals(addressSettings.getDefaultGroupFirstKey(), info.getDefaultGroupFirstKey());
      assertEquals(addressSettings.getDefaultMaxConsumers(), info.getDefaultMaxConsumers());
      assertEquals(addressSettings.isDefaultPurgeOnNoConsumers(), info.isDefaultPurgeOnNoConsumers());
      assertEquals(addressSettings.getDefaultConsumersBeforeDispatch(), info.getDefaultConsumersBeforeDispatch());
      assertEquals(addressSettings.getDefaultDelayBeforeDispatch(), info.getDefaultDelayBeforeDispatch());
      assertEquals(addressSettings.getDefaultQueueRoutingType(), info.getDefaultQueueRoutingType());
      assertEquals(addressSettings.getDefaultAddressRoutingType(), info.getDefaultAddressRoutingType());
      assertEquals(addressSettings.getDefaultConsumerWindowSize(), info.getDefaultConsumerWindowSize());
      assertEquals(addressSettings.getDefaultRingSize(), info.getDefaultRingSize());
      assertEquals(addressSettings.isAutoDeleteCreatedQueues(), info.isAutoDeleteCreatedQueues());
      assertEquals(addressSettings.getAutoDeleteQueuesDelay(), info.getAutoDeleteQueuesDelay());
      assertEquals(addressSettings.getAutoDeleteQueuesMessageCount(), info.getAutoDeleteQueuesMessageCount());
      assertEquals(addressSettings.getAutoDeleteAddressesDelay(), info.getAutoDeleteAddressesDelay());
      assertEquals(addressSettings.getRedeliveryCollisionAvoidanceFactor(), info.getRedeliveryCollisionAvoidanceFactor(), 0);
      assertEquals(addressSettings.getRetroactiveMessageCount(), info.getRetroactiveMessageCount());
      assertEquals(addressSettings.isAutoCreateDeadLetterResources(), info.isAutoCreateDeadLetterResources());
      assertEquals(addressSettings.getDeadLetterQueuePrefix(), info.getDeadLetterQueuePrefix());
      assertEquals(addressSettings.getDeadLetterQueueSuffix(), info.getDeadLetterQueueSuffix());
      assertEquals(addressSettings.isAutoCreateExpiryResources(), info.isAutoCreateExpiryResources());
      assertEquals(addressSettings.getExpiryQueuePrefix(), info.getExpiryQueuePrefix());
      assertEquals(addressSettings.getExpiryQueueSuffix(), info.getExpiryQueueSuffix());
      assertEquals(addressSettings.isEnableMetrics(), info.isEnableMetrics());
      assertEquals(addressSettings.isEnableManagementForInternal(), info.isEnableManagementForInternal());
   }
   @Test
   public void testAddressSettings() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      String addressMatch = "test.#";
      String exactAddress = "test.whatever";

      String DLA = "someDLA";
      String expiryAddress = "someExpiry";
      long expiryDelay = RandomUtil.randomPositiveLong();
      long minExpiryDelay = RandomUtil.randomPositiveLong();
      long maxExpiryDelay = RandomUtil.randomPositiveLong();
      boolean lastValueQueue = true;
      int deliveryAttempts = 1;
      long maxSizeBytes = 20;
      int pageSizeBytes = 10;
      int pageMaxCacheSize = 7;
      long redeliveryDelay = 4;
      double redeliveryMultiplier = 1;
      long maxRedeliveryDelay = 1000;
      long redistributionDelay = 5;
      boolean sendToDLAOnNoRoute = true;
      String addressFullMessagePolicy = "PAGE";
      long slowConsumerThreshold = 5;
      long slowConsumerCheckPeriod = 10;
      String slowConsumerPolicy = SlowConsumerPolicy.getType(RandomUtil.randomPositiveInt() % 2).toString();
      boolean autoCreateQueues = RandomUtil.randomBoolean();
      boolean autoDeleteQueues = RandomUtil.randomBoolean();
      boolean autoCreateAddresses = RandomUtil.randomBoolean();
      boolean autoDeleteAddresses = RandomUtil.randomBoolean();
      String configDeleteQueues = DeletionPolicy.getType(RandomUtil.randomPositiveInt() % 2).toString();
      String configDeleteAddresses = DeletionPolicy.getType(RandomUtil.randomPositiveInt() % 2).toString();
      long maxSizeBytesRejectThreshold = RandomUtil.randomPositiveLong();
      String defaultLastValueKey = RandomUtil.randomString();
      boolean defaultNonDestructive = RandomUtil.randomBoolean();
      boolean defaultExclusiveQueue = RandomUtil.randomBoolean();
      boolean defaultGroupRebalance = RandomUtil.randomBoolean();
      int defaultGroupBuckets = RandomUtil.randomPositiveInt();
      String defaultGroupFirstKey = RandomUtil.randomString();
      int defaultMaxConsumers = RandomUtil.randomPositiveInt();
      boolean defaultPurgeOnNoConsumers = RandomUtil.randomBoolean();
      int defaultConsumersBeforeDispatch = RandomUtil.randomPositiveInt();
      long defaultDelayBeforeDispatch = RandomUtil.randomPositiveLong();
      String defaultQueueRoutingType = RoutingType.getType((byte) (RandomUtil.randomPositiveInt() % 2)).toString();
      String defaultAddressRoutingType = RoutingType.getType((byte) (RandomUtil.randomPositiveInt() % 2)).toString();
      int defaultConsumerWindowSize = RandomUtil.randomPositiveInt();
      long defaultRingSize = RandomUtil.randomPositiveLong();
      boolean autoDeleteCreatedQueues = RandomUtil.randomBoolean();
      long autoDeleteQueuesDelay = RandomUtil.randomPositiveLong();
      long autoDeleteQueuesMessageCount = RandomUtil.randomPositiveLong();
      long autoDeleteAddressesDelay = RandomUtil.randomPositiveLong();
      double redeliveryCollisionAvoidanceFactor = RandomUtil.randomDouble();
      long retroactiveMessageCount = RandomUtil.randomPositiveLong();
      boolean autoCreateDeadLetterResources = RandomUtil.randomBoolean();
      String deadLetterQueuePrefix = RandomUtil.randomString();
      String deadLetterQueueSuffix = RandomUtil.randomString();
      boolean autoCreateExpiryResources = RandomUtil.randomBoolean();
      String expiryQueuePrefix = RandomUtil.randomString();
      String expiryQueueSuffix = RandomUtil.randomString();
      boolean enableMetrics = RandomUtil.randomBoolean();
      boolean enableManagementForInternal = RandomUtil.randomBoolean();

      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setDeadLetterAddress(new SimpleString(DLA))
              .setExpiryAddress(new SimpleString(expiryAddress))
              .setExpiryDelay(expiryDelay)
              .setDefaultLastValueQueue(lastValueQueue)
              .setMaxDeliveryAttempts(deliveryAttempts)
              .setMaxSizeBytes(maxSizeBytes)
              .setPageSizeBytes(pageSizeBytes)
              .setPageCacheMaxSize(pageMaxCacheSize)
              .setRedeliveryDelay(redeliveryDelay)
              .setRedeliveryMultiplier(redeliveryMultiplier)
              .setMaxRedeliveryDelay(maxRedeliveryDelay)
              .setRedistributionDelay(redistributionDelay)
              .setSendToDLAOnNoRoute(sendToDLAOnNoRoute)
              .setAddressFullMessagePolicy(AddressFullMessagePolicy.valueOf(addressFullMessagePolicy))
              .setSlowConsumerThreshold(slowConsumerThreshold)
              .setSlowConsumerCheckPeriod(slowConsumerCheckPeriod)
              .setSlowConsumerPolicy(SlowConsumerPolicy.valueOf(slowConsumerPolicy))
              .setAutoCreateQueues(autoCreateQueues)
              .setAutoDeleteQueues(autoDeleteQueues)
              .setAutoCreateAddresses(autoCreateAddresses)
              .setAutoDeleteAddresses(autoDeleteAddresses)
              .setConfigDeleteQueues(DeletionPolicy.valueOf(configDeleteQueues))
              .setConfigDeleteAddresses(DeletionPolicy.valueOf(configDeleteAddresses))
              .setMaxSizeBytesRejectThreshold(maxSizeBytesRejectThreshold)
              .setDefaultLastValueKey(SimpleString.toSimpleString(defaultLastValueKey))
              .setDefaultNonDestructive(defaultNonDestructive)
              .setDefaultExclusiveQueue(defaultExclusiveQueue)
              .setDefaultGroupRebalance(defaultGroupRebalance)
              .setDefaultGroupBuckets(defaultGroupBuckets)
              .setDefaultGroupFirstKey(SimpleString.toSimpleString(defaultGroupFirstKey))
              .setDefaultMaxConsumers(defaultMaxConsumers)
              .setDefaultPurgeOnNoConsumers(defaultPurgeOnNoConsumers)
              .setDefaultConsumersBeforeDispatch(defaultConsumersBeforeDispatch)
              .setDefaultDelayBeforeDispatch(defaultDelayBeforeDispatch)
              .setDefaultQueueRoutingType(RoutingType.valueOf(defaultQueueRoutingType))
              .setDefaultAddressRoutingType(RoutingType.valueOf(defaultAddressRoutingType))
              .setDefaultConsumerWindowSize(defaultConsumerWindowSize)
              .setDefaultRingSize(defaultRingSize)
              .setAutoDeleteCreatedQueues(autoDeleteCreatedQueues)
              .setAutoDeleteQueuesDelay(autoDeleteQueuesDelay)
              .setAutoDeleteQueuesMessageCount(autoDeleteQueuesMessageCount)
              .setAutoDeleteAddressesDelay(autoDeleteAddressesDelay)
              .setRedeliveryCollisionAvoidanceFactor(redeliveryCollisionAvoidanceFactor)
              .setRetroactiveMessageCount(retroactiveMessageCount)
              .setAutoCreateDeadLetterResources(autoCreateDeadLetterResources)
              .setDeadLetterQueuePrefix(SimpleString.toSimpleString(deadLetterQueuePrefix))
              .setDeadLetterQueueSuffix(SimpleString.toSimpleString(deadLetterQueueSuffix))
              .setAutoCreateExpiryResources(autoCreateExpiryResources)
              .setExpiryQueuePrefix(SimpleString.toSimpleString(expiryQueuePrefix))
              .setExpiryQueueSuffix(SimpleString.toSimpleString(expiryQueueSuffix))
              .setMinExpiryDelay(minExpiryDelay)
              .setMaxExpiryDelay(maxExpiryDelay)
              .setEnableMetrics(enableMetrics)
              .setEnableManagementForInternal(enableManagementForInternal);


      serverControl.addAddressSettings(addressMatch, addressSettings.toJSON());

      addressSettings.setMaxSizeBytes(100).setPageSizeBytes(1000);

      boolean ex = false;
      try {
         serverControl.addAddressSettings(addressMatch, addressSettings.toJSON());
      } catch (Exception expected) {
         ex = true;
      }

      assertTrue("Exception expected", ex);
      restartServer();
      serverControl = createManagementControl();

      String jsonString = serverControl.getAddressSettingsAsJSON(exactAddress);
      AddressSettingsInfo info = AddressSettingsInfo.fromJSON(jsonString);

      assertEquals(DLA, info.getDeadLetterAddress());
      assertEquals(expiryAddress, info.getExpiryAddress());
      assertEquals(expiryDelay, info.getExpiryDelay());
      assertEquals(minExpiryDelay, info.getMinExpiryDelay());
      assertEquals(maxExpiryDelay, info.getMaxExpiryDelay());
      assertEquals(lastValueQueue, info.isDefaultLastValueQueue());
      assertEquals(deliveryAttempts, info.getMaxDeliveryAttempts());
      assertEquals(maxSizeBytes, info.getMaxSizeBytes());
      assertEquals(pageMaxCacheSize, info.getPageCacheMaxSize());
      assertEquals(pageSizeBytes, info.getPageSizeBytes());
      assertEquals(redeliveryDelay, info.getRedeliveryDelay());
      assertEquals(redeliveryMultiplier, info.getRedeliveryMultiplier(), 0.000001);
      assertEquals(maxRedeliveryDelay, info.getMaxRedeliveryDelay());
      assertEquals(redistributionDelay, info.getRedistributionDelay());
      assertEquals(sendToDLAOnNoRoute, info.isSendToDLAOnNoRoute());
      assertEquals(addressFullMessagePolicy, info.getAddressFullMessagePolicy());
      assertEquals(slowConsumerThreshold, info.getSlowConsumerThreshold());
      assertEquals(slowConsumerCheckPeriod, info.getSlowConsumerCheckPeriod());
      assertEquals(slowConsumerPolicy, info.getSlowConsumerPolicy());
      assertEquals(autoCreateQueues, info.isAutoCreateQueues());
      assertEquals(autoDeleteQueues, info.isAutoDeleteQueues());
      assertEquals(autoCreateAddresses, info.isAutoCreateAddresses());
      assertEquals(autoDeleteAddresses, info.isAutoDeleteAddresses());
      assertEquals(configDeleteQueues, info.getConfigDeleteQueues());
      assertEquals(configDeleteAddresses, info.getConfigDeleteAddresses());
      assertEquals(maxSizeBytesRejectThreshold, info.getMaxSizeBytesRejectThreshold());
      assertEquals(defaultLastValueKey, info.getDefaultLastValueKey());
      assertEquals(defaultNonDestructive, info.isDefaultNonDestructive());
      assertEquals(defaultExclusiveQueue, info.isDefaultExclusiveQueue());
      assertEquals(defaultGroupRebalance, info.isDefaultGroupRebalance());
      assertEquals(defaultGroupBuckets, info.getDefaultGroupBuckets());
      assertEquals(defaultGroupFirstKey, info.getDefaultGroupFirstKey());
      assertEquals(defaultMaxConsumers, info.getDefaultMaxConsumers());
      assertEquals(defaultPurgeOnNoConsumers, info.isDefaultPurgeOnNoConsumers());
      assertEquals(defaultConsumersBeforeDispatch, info.getDefaultConsumersBeforeDispatch());
      assertEquals(defaultDelayBeforeDispatch, info.getDefaultDelayBeforeDispatch());
      assertEquals(defaultQueueRoutingType, info.getDefaultQueueRoutingType());
      assertEquals(defaultAddressRoutingType, info.getDefaultAddressRoutingType());
      assertEquals(defaultConsumerWindowSize, info.getDefaultConsumerWindowSize());
      assertEquals(defaultRingSize, info.getDefaultRingSize());
      assertEquals(autoDeleteCreatedQueues, info.isAutoDeleteCreatedQueues());
      assertEquals(autoDeleteQueuesDelay, info.getAutoDeleteQueuesDelay());
      assertEquals(autoDeleteQueuesMessageCount, info.getAutoDeleteQueuesMessageCount());
      assertEquals(autoDeleteAddressesDelay, info.getAutoDeleteAddressesDelay());
      assertEquals(redeliveryCollisionAvoidanceFactor, info.getRedeliveryCollisionAvoidanceFactor(), 0);
      assertEquals(retroactiveMessageCount, info.getRetroactiveMessageCount());
      assertEquals(autoCreateDeadLetterResources, info.isAutoCreateDeadLetterResources());
      assertEquals(deadLetterQueuePrefix, info.getDeadLetterQueuePrefix());
      assertEquals(deadLetterQueueSuffix, info.getDeadLetterQueueSuffix());
      assertEquals(autoCreateExpiryResources, info.isAutoCreateExpiryResources());
      assertEquals(expiryQueuePrefix, info.getExpiryQueuePrefix());
      assertEquals(expiryQueueSuffix, info.getExpiryQueueSuffix());
      assertEquals(enableMetrics, info.isEnableMetrics());
      assertEquals(enableManagementForInternal, info.isEnableManagementForInternal());


      addressSettings.setMaxSizeBytes(-1).setPageSizeBytes(1000);
      serverControl.addAddressSettings(addressMatch, addressSettings.toJSON());

      jsonString = serverControl.getAddressSettingsAsJSON(exactAddress);
      info = AddressSettingsInfo.fromJSON(jsonString);

      assertEquals(DLA, info.getDeadLetterAddress());
      assertEquals(expiryAddress, info.getExpiryAddress());
      assertEquals(expiryDelay, info.getExpiryDelay());
      assertEquals(minExpiryDelay, info.getMinExpiryDelay());
      assertEquals(maxExpiryDelay, info.getMaxExpiryDelay());
      assertEquals(lastValueQueue, info.isDefaultLastValueQueue());
      assertEquals(deliveryAttempts, info.getMaxDeliveryAttempts());
      assertEquals(-1, info.getMaxSizeBytes());
      assertEquals(pageMaxCacheSize, info.getPageCacheMaxSize());
      assertEquals(1000, info.getPageSizeBytes());
      assertEquals(redeliveryDelay, info.getRedeliveryDelay());
      assertEquals(redeliveryMultiplier, info.getRedeliveryMultiplier(), 0.000001);
      assertEquals(maxRedeliveryDelay, info.getMaxRedeliveryDelay());
      assertEquals(redistributionDelay, info.getRedistributionDelay());
      assertEquals(sendToDLAOnNoRoute, info.isSendToDLAOnNoRoute());
      assertEquals(addressFullMessagePolicy, info.getAddressFullMessagePolicy());
      assertEquals(slowConsumerThreshold, info.getSlowConsumerThreshold());
      assertEquals(slowConsumerCheckPeriod, info.getSlowConsumerCheckPeriod());
      assertEquals(slowConsumerPolicy, info.getSlowConsumerPolicy());
      assertEquals(autoCreateQueues, info.isAutoCreateQueues());
      assertEquals(autoDeleteQueues, info.isAutoDeleteQueues());
      assertEquals(autoCreateAddresses, info.isAutoCreateAddresses());
      assertEquals(autoDeleteAddresses, info.isAutoDeleteAddresses());
      assertEquals(configDeleteQueues, info.getConfigDeleteQueues());
      assertEquals(configDeleteAddresses, info.getConfigDeleteAddresses());
      assertEquals(maxSizeBytesRejectThreshold, info.getMaxSizeBytesRejectThreshold());
      assertEquals(defaultLastValueKey, info.getDefaultLastValueKey());
      assertEquals(defaultNonDestructive, info.isDefaultNonDestructive());
      assertEquals(defaultExclusiveQueue, info.isDefaultExclusiveQueue());
      assertEquals(defaultGroupRebalance, info.isDefaultGroupRebalance());
      assertEquals(defaultGroupBuckets, info.getDefaultGroupBuckets());
      assertEquals(defaultGroupFirstKey, info.getDefaultGroupFirstKey());
      assertEquals(defaultMaxConsumers, info.getDefaultMaxConsumers());
      assertEquals(defaultPurgeOnNoConsumers, info.isDefaultPurgeOnNoConsumers());
      assertEquals(defaultConsumersBeforeDispatch, info.getDefaultConsumersBeforeDispatch());
      assertEquals(defaultDelayBeforeDispatch, info.getDefaultDelayBeforeDispatch());
      assertEquals(defaultQueueRoutingType, info.getDefaultQueueRoutingType());
      assertEquals(defaultAddressRoutingType, info.getDefaultAddressRoutingType());
      assertEquals(defaultConsumerWindowSize, info.getDefaultConsumerWindowSize());
      assertEquals(defaultRingSize, info.getDefaultRingSize());
      assertEquals(autoDeleteCreatedQueues, info.isAutoDeleteCreatedQueues());
      assertEquals(autoDeleteQueuesDelay, info.getAutoDeleteQueuesDelay());
      assertEquals(autoDeleteQueuesMessageCount, info.getAutoDeleteQueuesMessageCount());
      assertEquals(autoDeleteAddressesDelay, info.getAutoDeleteAddressesDelay());
      assertEquals(redeliveryCollisionAvoidanceFactor, info.getRedeliveryCollisionAvoidanceFactor(), 0);
      assertEquals(retroactiveMessageCount, info.getRetroactiveMessageCount());
      assertEquals(autoCreateDeadLetterResources, info.isAutoCreateDeadLetterResources());
      assertEquals(deadLetterQueuePrefix, info.getDeadLetterQueuePrefix());
      assertEquals(deadLetterQueueSuffix, info.getDeadLetterQueueSuffix());
      assertEquals(autoCreateExpiryResources, info.isAutoCreateExpiryResources());
      assertEquals(expiryQueuePrefix, info.getExpiryQueuePrefix());
      assertEquals(expiryQueueSuffix, info.getExpiryQueueSuffix());
      assertEquals(enableMetrics, info.isEnableMetrics());
      assertEquals(enableManagementForInternal, info.isEnableManagementForInternal());

      addressSettings.setMaxSizeBytes(-2).setPageSizeBytes(1000);
      ex = false;
      try {
         serverControl.addAddressSettings(addressMatch, addressSettings.toJSON());
      } catch (Exception e) {
         ex = true;
      }

      assertTrue("Supposed to have an exception called", ex);

   }

   @Test
   public void testRemoveAddressSettingsEffective() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      String addr = "test";
      String root = "test.#";

      String DLA = "someDLA";
      String expiryAddress = "someExpiry";
      long expiryDelay = RandomUtil.randomPositiveLong();
      long minExpiryDelay = 10000;
      long maxExpiryDelay = 20000;
      boolean lastValueQueue = true;
      int deliveryAttempts = 1;
      long maxSizeBytes = 10 * 1024 * 1024;
      int pageSizeBytes = 1024 * 1024;
      int pageMaxCacheSize = 7;
      long redeliveryDelay = 4;
      double redeliveryMultiplier = 1;
      long maxRedeliveryDelay = 1000;
      long redistributionDelay = 5;
      boolean sendToDLAOnNoRoute = true;
      String addressFullMessagePolicy = "PAGE";
      long slowConsumerThreshold = 5;
      long slowConsumerCheckPeriod = 10;
      String slowConsumerPolicy = SlowConsumerPolicy.getType(RandomUtil.randomPositiveInt() % 2).toString();
      boolean autoCreateJmsQueues = RandomUtil.randomBoolean();
      boolean autoDeleteJmsQueues = RandomUtil.randomBoolean();
      boolean autoCreateJmsTopics = RandomUtil.randomBoolean();
      boolean autoDeleteJmsTopics = RandomUtil.randomBoolean();
      boolean autoCreateQueues = RandomUtil.randomBoolean();
      boolean autoDeleteQueues = RandomUtil.randomBoolean();
      boolean autoCreateAddresses = RandomUtil.randomBoolean();
      boolean autoDeleteAddresses = RandomUtil.randomBoolean();
      String configDeleteQueues = DeletionPolicy.getType(RandomUtil.randomPositiveInt() % 2).toString();
      String configDeleteAddresses = DeletionPolicy.getType(RandomUtil.randomPositiveInt() % 2).toString();
      long maxSizeBytesRejectThreshold = RandomUtil.randomPositiveLong();
      String defaultLastValueKey = RandomUtil.randomString();
      boolean defaultNonDestructive = RandomUtil.randomBoolean();
      boolean defaultExclusiveQueue = RandomUtil.randomBoolean();
      boolean defaultGroupRebalance = RandomUtil.randomBoolean();
      int defaultGroupBuckets = RandomUtil.randomPositiveInt();
      String defaultGroupFirstKey = RandomUtil.randomString();
      int defaultMaxConsumers = RandomUtil.randomPositiveInt();
      boolean defaultPurgeOnNoConsumers = RandomUtil.randomBoolean();
      int defaultConsumersBeforeDispatch = RandomUtil.randomPositiveInt();
      long defaultDelayBeforeDispatch = RandomUtil.randomPositiveLong();
      String defaultQueueRoutingType = RoutingType.getType((byte) (RandomUtil.randomPositiveInt() % 2)).toString();
      String defaultAddressRoutingType = RoutingType.getType((byte) (RandomUtil.randomPositiveInt() % 2)).toString();
      int defaultConsumerWindowSize = RandomUtil.randomPositiveInt();
      long defaultRingSize = RandomUtil.randomPositiveLong();
      boolean autoDeleteCreatedQueues = RandomUtil.randomBoolean();
      long autoDeleteQueuesDelay = RandomUtil.randomPositiveLong();
      long autoDeleteQueuesMessageCount = RandomUtil.randomPositiveLong();
      long autoDeleteAddressesDelay = RandomUtil.randomPositiveLong();
      double redeliveryCollisionAvoidanceFactor = RandomUtil.randomDouble();
      long retroactiveMessageCount = RandomUtil.randomPositiveLong();
      boolean autoCreateDeadLetterResources = RandomUtil.randomBoolean();
      String deadLetterQueuePrefix = RandomUtil.randomString();
      String deadLetterQueueSuffix = RandomUtil.randomString();
      boolean autoCreateExpiryResources = RandomUtil.randomBoolean();
      String expiryQueuePrefix = RandomUtil.randomString();
      String expiryQueueSuffix = RandomUtil.randomString();
      boolean enableMetrics = RandomUtil.randomBoolean();
      boolean enableManagementForInternal = RandomUtil.randomBoolean();

      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setDeadLetterAddress(new SimpleString(DLA))
              .setExpiryAddress(new SimpleString(expiryAddress))
              .setExpiryDelay(expiryDelay)
              .setDefaultLastValueQueue(lastValueQueue)
              .setMaxDeliveryAttempts(deliveryAttempts)
              .setMaxSizeBytes(maxSizeBytes)
              .setPageSizeBytes(pageSizeBytes)
              .setPageCacheMaxSize(pageMaxCacheSize)
              .setRedeliveryDelay(redeliveryDelay)
              .setRedeliveryMultiplier(redeliveryMultiplier)
              .setMaxRedeliveryDelay(maxRedeliveryDelay)
              .setRedistributionDelay(redistributionDelay)
              .setSendToDLAOnNoRoute(sendToDLAOnNoRoute)
              .setAddressFullMessagePolicy(AddressFullMessagePolicy.valueOf(addressFullMessagePolicy))
              .setSlowConsumerThreshold(slowConsumerThreshold)
              .setSlowConsumerCheckPeriod(slowConsumerCheckPeriod)
              .setSlowConsumerPolicy(SlowConsumerPolicy.valueOf(slowConsumerPolicy))
              .setAutoCreateQueues(autoCreateQueues)
              .setAutoDeleteQueues(autoDeleteQueues)
              .setAutoCreateAddresses(autoCreateAddresses)
              .setAutoDeleteAddresses(autoDeleteAddresses)
              .setConfigDeleteQueues(DeletionPolicy.valueOf(configDeleteQueues))
              .setConfigDeleteAddresses(DeletionPolicy.valueOf(configDeleteAddresses))
              .setMaxSizeBytesRejectThreshold(maxSizeBytesRejectThreshold)
              .setDefaultLastValueKey(SimpleString.toSimpleString(defaultLastValueKey))
              .setDefaultNonDestructive(defaultNonDestructive)
              .setDefaultExclusiveQueue(defaultExclusiveQueue)
              .setDefaultGroupRebalance(defaultGroupRebalance)
              .setDefaultGroupBuckets(defaultGroupBuckets)
              .setDefaultGroupFirstKey(SimpleString.toSimpleString(defaultGroupFirstKey))
              .setDefaultMaxConsumers(defaultMaxConsumers)
              .setDefaultPurgeOnNoConsumers(defaultPurgeOnNoConsumers)
              .setDefaultConsumersBeforeDispatch(defaultConsumersBeforeDispatch)
              .setDefaultDelayBeforeDispatch(defaultDelayBeforeDispatch)
              .setDefaultQueueRoutingType(RoutingType.valueOf(defaultQueueRoutingType))
              .setDefaultAddressRoutingType(RoutingType.valueOf(defaultAddressRoutingType))
              .setDefaultConsumerWindowSize(defaultConsumerWindowSize)
              .setDefaultRingSize(defaultRingSize)
              .setAutoDeleteCreatedQueues(autoDeleteCreatedQueues)
              .setAutoDeleteQueuesDelay(autoDeleteQueuesDelay)
              .setAutoDeleteQueuesMessageCount(autoDeleteQueuesMessageCount)
              .setAutoDeleteAddressesDelay(autoDeleteAddressesDelay)
              .setRedeliveryCollisionAvoidanceFactor(redeliveryCollisionAvoidanceFactor)
              .setRetroactiveMessageCount(retroactiveMessageCount)
              .setAutoCreateDeadLetterResources(autoCreateDeadLetterResources)
              .setDeadLetterQueuePrefix(SimpleString.toSimpleString(deadLetterQueuePrefix))
              .setDeadLetterQueueSuffix(SimpleString.toSimpleString(deadLetterQueueSuffix))
              .setAutoCreateExpiryResources(autoCreateExpiryResources)
              .setExpiryQueuePrefix(SimpleString.toSimpleString(expiryQueuePrefix))
              .setExpiryQueueSuffix(SimpleString.toSimpleString(expiryQueueSuffix))
              .setMinExpiryDelay(minExpiryDelay)
              .setMaxExpiryDelay(maxExpiryDelay)
              .setEnableMetrics(enableMetrics)
              .setEnableManagementForInternal(enableManagementForInternal);

      serverControl.addAddressSettings(root, addressSettings.toJSON());

      AddressSettingsInfo rootInfo = AddressSettingsInfo.fromJSON(serverControl.getAddressSettingsAsJSON(root));

      // Give settings for addr different values to the root
      final long addrMinExpiryDelay = rootInfo.getMinExpiryDelay() + 1;
      final long addrMaxExpiryDelay = rootInfo.getMaxExpiryDelay() - 1;

      addressSettings.setMinExpiryDelay(addrMinExpiryDelay).setMaxExpiryDelay(addrMaxExpiryDelay);
      serverControl.addAddressSettings(addr, addressSettings.toJSON());
      AddressSettingsInfo addrInfo = AddressSettingsInfo.fromJSON(serverControl.getAddressSettingsAsJSON(addr));

      assertEquals("settings for addr should carry update", addrMinExpiryDelay, addrInfo.getMinExpiryDelay());
      assertEquals("settings for addr should carry update", addrMaxExpiryDelay, addrInfo.getMaxExpiryDelay());

      serverControl.removeAddressSettings(addr);

      AddressSettingsInfo rereadAddrInfo = AddressSettingsInfo.fromJSON(serverControl.getAddressSettingsAsJSON(addr));

      assertEquals("settings for addr should have reverted to original value after removal", rootInfo.getMinExpiryDelay(), rereadAddrInfo.getMinExpiryDelay());
      assertEquals("settings for addr should have reverted to original value after removal", rootInfo.getMaxExpiryDelay(), rereadAddrInfo.getMaxExpiryDelay());
   }

   @Test
   public void testNullRouteNameOnDivert() throws Exception {
      String address = RandomUtil.randomString();
      String name = RandomUtil.randomString();
      String forwardingAddress = RandomUtil.randomString();

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(name, address));
      assertEquals(0, serverControl.getDivertNames().length);

      serverControl.createDivert(name.toString(), null, address, forwardingAddress, true, null, null);

      checkResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(name, address));
   }

   @Test
   public void testCreateAndDestroyDivert() throws Exception {
      String address = RandomUtil.randomString();
      String name = RandomUtil.randomString();
      String routingName = RandomUtil.randomString();
      String forwardingAddress = RandomUtil.randomString();

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(name, address));
      assertEquals(0, serverControl.getDivertNames().length);

      serverControl.createDivert(name.toString(), routingName, address, forwardingAddress, true, null, null);

      checkResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(name, address));
      DivertControl divertControl = ManagementControlHelper.createDivertControl(name.toString(), address, mbeanServer);
      assertEquals(name.toString(), divertControl.getUniqueName());
      assertEquals(address, divertControl.getAddress());
      assertEquals(forwardingAddress, divertControl.getForwardingAddress());
      assertEquals(routingName, divertControl.getRoutingName());
      assertTrue(divertControl.isExclusive());
      assertNull(divertControl.getFilter());
      assertNull(divertControl.getTransformerClassName());
      String[] divertNames = serverControl.getDivertNames();
      assertEquals(1, divertNames.length);
      assertEquals(name, divertNames[0]);

      // check that a message sent to the address is diverted exclusively
      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = csf.createSession();

      String divertQueue = RandomUtil.randomString();
      String queue = RandomUtil.randomString();
      if (legacyCreateQueue) {
         session.createQueue(forwardingAddress, RoutingType.ANYCAST, divertQueue);
         session.createQueue(address, RoutingType.ANYCAST, queue);
      } else {
         session.createQueue(new QueueConfiguration(divertQueue).setAddress(forwardingAddress).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         session.createQueue(new QueueConfiguration(queue).setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(false);
      String text = RandomUtil.randomString();
      message.putStringProperty("prop", text);
      producer.send(message);

      ClientConsumer consumer = session.createConsumer(queue);
      ClientConsumer divertedConsumer = session.createConsumer(divertQueue);

      session.start();

      assertNull(consumer.receiveImmediate());
      message = divertedConsumer.receive(5000);
      assertNotNull(message);
      assertEquals(text, message.getStringProperty("prop"));

      serverControl.destroyDivert(name.toString());

      checkNoResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(name, address));
      assertEquals(0, serverControl.getDivertNames().length);

      // check that a message is no longer diverted
      message = session.createMessage(false);
      String text2 = RandomUtil.randomString();
      message.putStringProperty("prop", text2);
      producer.send(message);

      assertNull(divertedConsumer.receiveImmediate());
      message = consumer.receive(5000);
      assertNotNull(message);
      assertEquals(text2, message.getStringProperty("prop"));

      consumer.close();
      divertedConsumer.close();
      session.deleteQueue(queue);
      session.deleteQueue(divertQueue);
      session.close();

      locator.close();

   }

   @Test
   public void testCreateAndUpdateDivert() throws Exception {
      String address = RandomUtil.randomString();
      String name = RandomUtil.randomString();
      String routingName = RandomUtil.randomString();
      String forwardingAddress = RandomUtil.randomString();
      String updatedForwardingAddress = RandomUtil.randomString();

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(name, address));
      assertEquals(0, serverControl.getDivertNames().length);

      serverControl.createDivert(name.toString(), routingName, address, forwardingAddress, true, null, null);

      checkResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(name, address));
      DivertControl divertControl = ManagementControlHelper.createDivertControl(name.toString(), address, mbeanServer);
      assertEquals(name.toString(), divertControl.getUniqueName());
      assertEquals(address, divertControl.getAddress());
      assertEquals(forwardingAddress, divertControl.getForwardingAddress());
      assertEquals(routingName, divertControl.getRoutingName());
      assertTrue(divertControl.isExclusive());
      assertNull(divertControl.getFilter());
      assertNull(divertControl.getTransformerClassName());
      String[] divertNames = serverControl.getDivertNames();
      assertEquals(1, divertNames.length);
      assertEquals(name, divertNames[0]);

      // check that a message sent to the address is diverted exclusively
      ServerLocator locator = createInVMNonHALocator();

      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = csf.createSession();

      String updatedDivertQueue = RandomUtil.randomString();
      String divertQueue = RandomUtil.randomString();
      String queue = RandomUtil.randomString();
      if (legacyCreateQueue) {
         session.createQueue(updatedForwardingAddress, RoutingType.ANYCAST, updatedDivertQueue);
         session.createQueue(forwardingAddress, RoutingType.ANYCAST, divertQueue);
         session.createQueue(address, RoutingType.ANYCAST, queue);
      } else {
         session.createQueue(new QueueConfiguration(updatedDivertQueue).setAddress(updatedForwardingAddress).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         session.createQueue(new QueueConfiguration(divertQueue).setAddress(forwardingAddress).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         session.createQueue(new QueueConfiguration(queue).setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientProducer producer = session.createProducer(address);
      ClientMessage message = session.createMessage(false);
      String text = RandomUtil.randomString();
      message.putStringProperty("prop", text);
      producer.send(message);

      ClientConsumer consumer = session.createConsumer(queue);
      ClientConsumer divertedConsumer = session.createConsumer(divertQueue);
      ClientConsumer updatedDivertedConsumer = session.createConsumer(updatedDivertQueue);

      session.start();

      assertNull(consumer.receiveImmediate());
      message = divertedConsumer.receive(5000);
      assertNotNull(message);
      assertEquals(text, message.getStringProperty("prop"));
      assertNull(updatedDivertedConsumer.receiveImmediate());

      serverControl.updateDivert(name.toString(), updatedForwardingAddress, null, null, null, ActiveMQDefaultConfiguration.getDefaultDivertRoutingType());

      checkResource(ObjectNameBuilder.DEFAULT.getDivertObjectName(name, address));
      divertControl = ManagementControlHelper.createDivertControl(name.toString(), address, mbeanServer);
      assertEquals(name.toString(), divertControl.getUniqueName());
      assertEquals(address, divertControl.getAddress());
      assertEquals(updatedForwardingAddress, divertControl.getForwardingAddress());
      assertEquals(routingName, divertControl.getRoutingName());
      assertTrue(divertControl.isExclusive());
      assertNull(divertControl.getFilter());
      assertNull(divertControl.getTransformerClassName());
      divertNames = serverControl.getDivertNames();
      assertEquals(1, divertNames.length);
      assertEquals(name, divertNames[0]);
      //now check its been persisted
      PersistedDivertConfiguration pdc = server.getStorageManager().recoverDivertConfigurations().get(0);
      assertEquals(pdc.getDivertConfiguration().getForwardingAddress(), updatedForwardingAddress);

      // check that a message is no longer exclusively diverted
      message = session.createMessage(false);
      String text2 = RandomUtil.randomString();
      message.putStringProperty("prop", text2);
      producer.send(message);

      assertNull(consumer.receiveImmediate());
      assertNull(divertedConsumer.receiveImmediate());
      message = updatedDivertedConsumer.receive(5000);
      assertNotNull(message);
      assertEquals(text2, message.getStringProperty("prop"));

      consumer.close();
      divertedConsumer.close();
      updatedDivertedConsumer.close();
      session.deleteQueue(queue);
      session.deleteQueue(divertQueue);
      session.deleteQueue(updatedDivertQueue);
      session.close();

      locator.close();

   }

   @Test
   public void testCreateAndDestroyBridge() throws Exception {
      String name = RandomUtil.randomString();
      String sourceAddress = RandomUtil.randomString();
      String sourceQueue = RandomUtil.randomString();
      String targetAddress = RandomUtil.randomString();
      String targetQueue = RandomUtil.randomString();

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getBridgeObjectName(name));
      assertEquals(0, serverControl.getBridgeNames().length);

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = csf.createSession();

      if (legacyCreateQueue) {
         session.createQueue(sourceAddress, RoutingType.ANYCAST, sourceQueue);
         session.createQueue(targetAddress, RoutingType.ANYCAST, targetQueue);
      } else {
         session.createQueue(new QueueConfiguration(sourceQueue).setAddress(sourceAddress).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         session.createQueue(new QueueConfiguration(targetQueue).setAddress(targetAddress).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      serverControl.createBridge(name, sourceQueue, targetAddress, null, // forwardingAddress
                                 null, // filterString
                                 ActiveMQClient.DEFAULT_RETRY_INTERVAL, ActiveMQClient.DEFAULT_RETRY_INTERVAL_MULTIPLIER, ActiveMQClient.INITIAL_CONNECT_ATTEMPTS, ActiveMQClient.DEFAULT_RECONNECT_ATTEMPTS, false, // duplicateDetection
                                 1, // confirmationWindowSize
                                 -1, // producerWindowSize
                                 ActiveMQClient.DEFAULT_CLIENT_FAILURE_CHECK_PERIOD, connectorConfig.getName(), // liveConnector
                                 false, false, null, null);

      checkResource(ObjectNameBuilder.DEFAULT.getBridgeObjectName(name));
      String[] bridgeNames = serverControl.getBridgeNames();
      assertEquals(1, bridgeNames.length);
      assertEquals(name, bridgeNames[0]);

      BridgeControl bridgeControl = ManagementControlHelper.createBridgeControl(name, mbeanServer);
      assertEquals(name, bridgeControl.getName());
      assertTrue(bridgeControl.isStarted());

      // check that a message sent to the sourceAddress is put in the tagetQueue
      ClientProducer producer = session.createProducer(sourceAddress);
      ClientMessage message = session.createMessage(false);
      String text = RandomUtil.randomString();
      message.putStringProperty("prop", text);
      producer.send(message);

      session.start();

      ClientConsumer targetConsumer = session.createConsumer(targetQueue);
      message = targetConsumer.receive(5000);
      assertNotNull(message);
      assertEquals(text, message.getStringProperty("prop"));

      ClientConsumer sourceConsumer = session.createConsumer(sourceQueue);
      assertNull(sourceConsumer.receiveImmediate());

      serverControl.destroyBridge(name);

      checkNoResource(ObjectNameBuilder.DEFAULT.getBridgeObjectName(name));
      assertEquals(0, serverControl.getBridgeNames().length);

      // check that a message is no longer diverted
      message = session.createMessage(false);
      String text2 = RandomUtil.randomString();
      message.putStringProperty("prop", text2);
      producer.send(message);

      assertNull(targetConsumer.receiveImmediate());
      message = sourceConsumer.receive(5000);
      assertNotNull(message);
      assertEquals(text2, message.getStringProperty("prop"));

      sourceConsumer.close();
      targetConsumer.close();

      session.deleteQueue(sourceQueue);
      session.deleteQueue(targetQueue);

      session.close();

      locator.close();
   }

   @Test
   public void testCreateAndDestroyBridgeFromJson() throws Exception {
      internalTestCreateAndDestroyBridgeFromJson(false);
   }

   @Test
   public void testCreateAndDestroyBridgeFromJsonDynamicConnector() throws Exception {
      internalTestCreateAndDestroyBridgeFromJson(true);
   }

   private void internalTestCreateAndDestroyBridgeFromJson(boolean dynamicConnector) throws Exception {
      String name = RandomUtil.randomString();
      String sourceAddress = RandomUtil.randomString();
      String sourceQueue = RandomUtil.randomString();
      String targetAddress = RandomUtil.randomString();
      String targetQueue = RandomUtil.randomString();

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getBridgeObjectName(name));
      assertEquals(0, serverControl.getBridgeNames().length);

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = csf.createSession();

      if (legacyCreateQueue) {
         session.createQueue(sourceAddress, RoutingType.ANYCAST, sourceQueue);
         session.createQueue(targetAddress, RoutingType.ANYCAST, targetQueue);
      } else {
         session.createQueue(new QueueConfiguration(sourceQueue).setAddress(sourceAddress).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         session.createQueue(new QueueConfiguration(targetQueue).setAddress(targetAddress).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      String connectorName = connectorConfig.getName();
      if (dynamicConnector) {
         connectorName = RandomUtil.randomString();
         serverControl.addConnector(connectorName, "vm://0");
      }

      BridgeConfiguration bridgeConfiguration = new BridgeConfiguration(name)
         .setQueueName(sourceQueue)
         .setForwardingAddress(targetAddress)
         .setUseDuplicateDetection(false)
         .setConfirmationWindowSize(1)
         .setProducerWindowSize(-1)
         .setStaticConnectors(Collections.singletonList(connectorName))
         .setHA(false)
         .setUser(null)
         .setPassword(null);
      serverControl.createBridge(bridgeConfiguration.toJSON());

      checkResource(ObjectNameBuilder.DEFAULT.getBridgeObjectName(name));
      String[] bridgeNames = serverControl.getBridgeNames();
      assertEquals(1, bridgeNames.length);
      assertEquals(name, bridgeNames[0]);

      BridgeControl bridgeControl = ManagementControlHelper.createBridgeControl(name, mbeanServer);
      assertEquals(name, bridgeControl.getName());
      assertTrue(bridgeControl.isStarted());

      // check that a message sent to the sourceAddress is put in the tagetQueue
      ClientProducer producer = session.createProducer(sourceAddress);
      ClientMessage message = session.createMessage(false);
      String text = RandomUtil.randomString();
      message.putStringProperty("prop", text);
      producer.send(message);

      session.start();

      ClientConsumer targetConsumer = session.createConsumer(targetQueue);
      message = targetConsumer.receive(5000);
      assertNotNull(message);
      assertEquals(text, message.getStringProperty("prop"));

      ClientConsumer sourceConsumer = session.createConsumer(sourceQueue);
      assertNull(sourceConsumer.receiveImmediate());

      serverControl.destroyBridge(name);

      checkNoResource(ObjectNameBuilder.DEFAULT.getBridgeObjectName(name));
      assertEquals(0, serverControl.getBridgeNames().length);

      // check that a message is no longer diverted
      message = session.createMessage(false);
      String text2 = RandomUtil.randomString();
      message.putStringProperty("prop", text2);
      producer.send(message);

      assertNull(targetConsumer.receiveImmediate());
      message = sourceConsumer.receive(5000);
      assertNotNull(message);
      assertEquals(text2, message.getStringProperty("prop"));

      sourceConsumer.close();
      targetConsumer.close();

      session.deleteQueue(sourceQueue);
      session.deleteQueue(targetQueue);

      session.close();

      locator.close();
   }

   @Test
   public void testAddAndRemoveConnector() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      String connectorName = RandomUtil.randomString();
      serverControl.addConnector(connectorName, "vm://0");
      assertEquals(connectorName, server.getConfiguration().getConnectorConfigurations().get(connectorName).getName());
      serverControl.removeConnector(connectorName);
      assertNull(server.getConfiguration().getConnectorConfigurations().get(connectorName));
   }

   @Test
   public void testListPreparedTransactionDetails() throws Exception {
      SimpleString atestq = new SimpleString("BasicXaTestq");
      Xid xid = newXID();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession clientSession = csf.createSession(true, false, false);
      if (legacyCreateQueue) {
         clientSession.createQueue(atestq, atestq, null, true);
      } else {
         clientSession.createQueue(new QueueConfiguration(atestq).setDurable(true));
      }

      ClientMessage m1 = createTextMessage(clientSession, "");
      ClientMessage m2 = createTextMessage(clientSession, "");
      ClientMessage m3 = createTextMessage(clientSession, "");
      ClientMessage m4 = createTextMessage(clientSession, "");
      m1.putStringProperty("m1", "m1");
      m2.putStringProperty("m2", "m2");
      m3.putStringProperty("m3", "m3");
      m4.putStringProperty("m4", "m4");
      ClientProducer clientProducer = clientSession.createProducer(atestq);
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m1);
      clientProducer.send(m2);
      clientProducer.send(m3);
      clientProducer.send(m4);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      // add another TX, but don't prepare it
      ClientMessage m5 = createTextMessage(clientSession, "");
      ClientMessage m6 = createTextMessage(clientSession, "");
      ClientMessage m7 = createTextMessage(clientSession, "");
      ClientMessage m8 = createTextMessage(clientSession, "");
      m5.putStringProperty("m5", "m5");
      m6.putStringProperty("m6", "m6");
      m7.putStringProperty("m7", "m7");
      m8.putStringProperty("m8", "m8");
      Xid xid2 = newXID();
      clientSession.start(xid2, XAResource.TMNOFLAGS);
      clientProducer.send(m5);
      clientProducer.send(m6);
      clientProducer.send(m7);
      clientProducer.send(m8);
      clientSession.end(xid2, XAResource.TMSUCCESS);

      ActiveMQServerControl serverControl = createManagementControl();

      JsonArray jsonArray = JsonUtil.readJsonArray(serverControl.listProducersInfoAsJSON());

      assertEquals(1 + extraProducers, jsonArray.size());
      JsonObject first = (JsonObject) jsonArray.get(0);
      if (!first.getString(ProducerField.ADDRESS.getAlternativeName()).equalsIgnoreCase(atestq.toString())) {
         first = (JsonObject) jsonArray.get(1);
      }
      assertEquals(8, ((JsonObject) first).getInt("msgSent"));
      assertTrue(((JsonObject) first).getInt("msgSizeSent") > 0);

      clientSession.close();
      locator.close();

      String txDetails = serverControl.listPreparedTransactionDetailsAsJSON();

      Assert.assertTrue(txDetails.matches(".*m1.*"));
      Assert.assertTrue(txDetails.matches(".*m2.*"));
      Assert.assertTrue(txDetails.matches(".*m3.*"));
      Assert.assertTrue(txDetails.matches(".*m4.*"));
      Assert.assertFalse(txDetails.matches(".*m5.*"));
      Assert.assertFalse(txDetails.matches(".*m6.*"));
      Assert.assertFalse(txDetails.matches(".*m7.*"));
      Assert.assertFalse(txDetails.matches(".*m8.*"));
   }

   @Test
   public void testListPreparedTransactionDetailsOnConsumer() throws Exception {
      SimpleString atestq = new SimpleString("BasicXaTestq");
      Xid xid = newXID();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession clientSession = csf.createSession(true, false, false);
      if (legacyCreateQueue) {
         clientSession.createQueue(atestq, atestq, null, true);
      } else {
         clientSession.createQueue(new QueueConfiguration(atestq));
      }

      ClientMessage m1 = createTextMessage(clientSession, "");
      ClientMessage m2 = createTextMessage(clientSession, "");
      ClientMessage m3 = createTextMessage(clientSession, "");
      ClientMessage m4 = createTextMessage(clientSession, "");
      m1.putStringProperty("m1", "valuem1");
      m2.putStringProperty("m2", "valuem2");
      m3.putStringProperty("m3", "valuem3");
      m4.putStringProperty("m4", "valuem4");
      ClientProducer clientProducer = clientSession.createProducer(atestq);
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m1);
      clientProducer.send(m2);
      clientProducer.send(m3);
      clientProducer.send(m4);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);
      clientSession.commit(xid, false);

      ClientConsumer consumer = clientSession.createConsumer(atestq);
      clientSession.start();
      xid = newXID();
      clientSession.start(xid, XAResource.TMNOFLAGS);
      m1 = consumer.receive(1000);
      Assert.assertNotNull(m1);
      m1.acknowledge();
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);
      ActiveMQServerControl serverControl = createManagementControl();
      String jsonOutput = serverControl.listPreparedTransactionDetailsAsJSON();

      // just one message is pending, and it should be listed on the output
      Assert.assertTrue(jsonOutput.lastIndexOf("valuem1") > 0);
      Assert.assertTrue(jsonOutput.lastIndexOf("valuem2") < 0);
      Assert.assertTrue(jsonOutput.lastIndexOf("valuem3") < 0);
      Assert.assertTrue(jsonOutput.lastIndexOf("valuem4") < 0);
      clientSession.close();
      locator.close();
   }

   @Test
   public void testListPreparedTransactionDetailsAsHTML() throws Exception {
      SimpleString atestq = new SimpleString("BasicXaTestq");
      Xid xid = newXID();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession clientSession = csf.createSession(true, false, false);
      if (legacyCreateQueue) {
         clientSession.createQueue(atestq, atestq, null, true);
      } else {
         clientSession.createQueue(new QueueConfiguration(atestq));
      }

      ClientMessage m1 = createTextMessage(clientSession, "");
      ClientMessage m2 = createTextMessage(clientSession, "");
      ClientMessage m3 = createTextMessage(clientSession, "");
      ClientMessage m4 = createTextMessage(clientSession, "");
      m1.putStringProperty("m1", "m1");
      m2.putStringProperty("m2", "m2");
      m3.putStringProperty("m3", "m3");
      m4.putStringProperty("m4", "m4");
      ClientProducer clientProducer = clientSession.createProducer(atestq);
      clientSession.start(xid, XAResource.TMNOFLAGS);
      clientProducer.send(m1);
      clientProducer.send(m2);
      clientProducer.send(m3);
      clientProducer.send(m4);
      clientSession.end(xid, XAResource.TMSUCCESS);
      clientSession.prepare(xid);

      clientSession.close();
      locator.close();

      ActiveMQServerControl serverControl = createManagementControl();
      String html = serverControl.listPreparedTransactionDetailsAsHTML();

      Assert.assertTrue(html.matches(".*m1.*"));
      Assert.assertTrue(html.matches(".*m2.*"));
      Assert.assertTrue(html.matches(".*m3.*"));
      Assert.assertTrue(html.matches(".*m4.*"));
   }

   @Test
   public void testCommitPreparedTransactions() throws Exception {
      SimpleString recQueue = new SimpleString("BasicXaTestqRec");
      SimpleString sendQueue = new SimpleString("BasicXaTestqSend");

      byte[] globalTransactionId = UUIDGenerator.getInstance().generateStringUUID().getBytes();
      Xid xid = new XidImpl("xa1".getBytes(), 1, globalTransactionId);
      Xid xid2 = new XidImpl("xa2".getBytes(), 1, globalTransactionId);
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession clientSession = csf.createSession(true, false, false);
      if (legacyCreateQueue) {
         clientSession.createQueue(recQueue, recQueue, null, true);
         clientSession.createQueue(sendQueue, sendQueue, null, true);
      } else {
         clientSession.createQueue(new QueueConfiguration(recQueue));
         clientSession.createQueue(new QueueConfiguration(sendQueue));
      }
      ClientMessage m1 = createTextMessage(clientSession, "");
      m1.putStringProperty("m1", "m1");
      ClientProducer clientProducer = clientSession.createProducer(recQueue);
      clientProducer.send(m1);
      locator.close();

      ServerLocator receiveLocator = createInVMNonHALocator();
      ClientSessionFactory receiveCsf = createSessionFactory(receiveLocator);
      ClientSession receiveClientSession = receiveCsf.createSession(true, false, false);
      ClientConsumer consumer = receiveClientSession.createConsumer(recQueue);

      ServerLocator sendLocator = createInVMNonHALocator();
      ClientSessionFactory sendCsf = createSessionFactory(sendLocator);
      ClientSession sendClientSession = sendCsf.createSession(true, false, false);
      ClientProducer producer = sendClientSession.createProducer(sendQueue);

      receiveClientSession.start(xid, XAResource.TMNOFLAGS);
      receiveClientSession.start();
      sendClientSession.start(xid2, XAResource.TMNOFLAGS);

      ClientMessage m = consumer.receive(5000);
      assertNotNull(m);

      producer.send(m);

      receiveClientSession.end(xid, XAResource.TMSUCCESS);
      sendClientSession.end(xid2, XAResource.TMSUCCESS);

      receiveClientSession.prepare(xid);
      sendClientSession.prepare(xid2);

      ActiveMQServerControl serverControl = createManagementControl();

      sendLocator.close();
      receiveLocator.close();

      boolean success = serverControl.commitPreparedTransaction(XidImpl.toBase64String(xid));

      success = serverControl.commitPreparedTransaction(XidImpl.toBase64String(xid));
   }

   @Test
   public void testScaleDownWithConnector() throws Exception {
      scaleDown(new ScaleDownHandler() {
         @Override
         public void scaleDown(ActiveMQServerControl control) throws Exception {
            control.scaleDown("server2-connector");
         }
      });
   }

   @Test
   public void testScaleDownWithOutConnector() throws Exception {
      scaleDown(new ScaleDownHandler() {
         @Override
         public void scaleDown(ActiveMQServerControl control) throws Exception {
            control.scaleDown(null);
         }
      });
   }

   @Test
   public void testForceFailover() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      try {
         serverControl.forceFailover();
      } catch (Exception e) {
         if (!usingCore()) {
            fail(e.getMessage());
         }
      }
      Wait.waitFor(() -> !server.isStarted());
      assertFalse(server.isStarted());
   }

   @Test
   public void testTotalMessageCount() throws Exception {
      String random1 = RandomUtil.randomString();
      String random2 = RandomUtil.randomString();

      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = csf.createSession();

      if (legacyCreateQueue) {
         session.createQueue(random1, RoutingType.ANYCAST, random1);
         session.createQueue(random2, RoutingType.ANYCAST, random2);
      } else {
         session.createQueue(new QueueConfiguration(random1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         session.createQueue(new QueueConfiguration(random2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientProducer producer1 = session.createProducer(random1);
      ClientProducer producer2 = session.createProducer(random2);
      ClientMessage message = session.createMessage(true);
      producer1.send(message);
      producer2.send(message);

      session.commit();

      // flush executors on queues so we can get precise number of messages
      Queue queue1 = server.locateQueue(SimpleString.toSimpleString(random1));
      queue1.flushExecutor();
      Queue queue2 = server.locateQueue(SimpleString.toSimpleString(random1));
      queue2.flushExecutor();

      assertEquals(2, serverControl.getTotalMessageCount());

      session.deleteQueue(random1);
      session.deleteQueue(random2);

      session.close();

      locator.close();
   }

   @Test
   public void testTotalConnectionCount() throws Exception {
      final int CONNECTION_COUNT = 100;

      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      for (int i = 0; i < CONNECTION_COUNT; i++) {
         createSessionFactory(locator).close();
      }

      assertEquals(CONNECTION_COUNT + (usingCore() ? 1 : 0), serverControl.getTotalConnectionCount());
      assertEquals((usingCore() ? 1 : 0), serverControl.getConnectionCount());

      locator.close();
   }

   @Test
   public void testTotalMessagesAdded() throws Exception {
      String random1 = RandomUtil.randomString();
      String random2 = RandomUtil.randomString();

      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = csf.createSession();

      if (legacyCreateQueue) {
         session.createQueue(random1, RoutingType.ANYCAST, random1);
         session.createQueue(random2, RoutingType.ANYCAST, random2);
      } else {
         session.createQueue(new QueueConfiguration(random1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         session.createQueue(new QueueConfiguration(random2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientProducer producer1 = session.createProducer(random1);
      ClientProducer producer2 = session.createProducer(random2);
      ClientMessage message = session.createMessage(false);
      producer1.send(message);
      producer2.send(message);

      session.commit();

      ClientConsumer consumer1 = session.createConsumer(random1);
      ClientConsumer consumer2 = session.createConsumer(random2);

      session.start();

      assertNotNull(consumer1.receive().acknowledge());
      assertNotNull(consumer2.receive().acknowledge());

      session.commit();

      assertEquals(2, serverControl.getTotalMessagesAdded());
      assertEquals(0, serverControl.getTotalMessageCount());

      consumer1.close();
      consumer2.close();

      session.deleteQueue(random1);
      session.deleteQueue(random2);

      session.close();

      locator.close();
   }


   @Test
   public void testTotalMessagesAcknowledged() throws Exception {
      String random1 = RandomUtil.randomString();
      String random2 = RandomUtil.randomString();

      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = csf.createSession();

      if (legacyCreateQueue) {
         session.createQueue(random1, RoutingType.ANYCAST, random1);
         session.createQueue(random2, RoutingType.ANYCAST, random2);
      } else {
         session.createQueue(new QueueConfiguration(random1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         session.createQueue(new QueueConfiguration(random2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientProducer producer1 = session.createProducer(random1);
      ClientProducer producer2 = session.createProducer(random2);
      ClientMessage message = session.createMessage(false);
      producer1.send(message);
      producer2.send(message);

      session.commit();

      ClientConsumer consumer1 = session.createConsumer(random1);
      ClientConsumer consumer2 = session.createConsumer(random2);

      session.start();

      assertNotNull(consumer1.receive().acknowledge());
      assertNotNull(consumer2.receive().acknowledge());

      session.commit();

      assertEquals(2, serverControl.getTotalMessagesAcknowledged());
      assertEquals(0, serverControl.getTotalMessageCount());

      consumer1.close();
      consumer2.close();

      session.deleteQueue(random1);
      session.deleteQueue(random2);

      session.close();

      locator.close();
   }

   @Test
   public void testTotalConsumerCount() throws Exception {
      String random1 = RandomUtil.randomString();
      String random2 = RandomUtil.randomString();

      ActiveMQServerControl serverControl = createManagementControl();
      QueueControl queueControl1 = ManagementControlHelper.createQueueControl(SimpleString.toSimpleString(random1), SimpleString.toSimpleString(random1), RoutingType.ANYCAST, mbeanServer);
      QueueControl queueControl2 = ManagementControlHelper.createQueueControl(SimpleString.toSimpleString(random2), SimpleString.toSimpleString(random2), RoutingType.ANYCAST, mbeanServer);

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = csf.createSession();

      if (legacyCreateQueue) {
         session.createQueue(random1, RoutingType.ANYCAST, random1);
         session.createQueue(random2, RoutingType.ANYCAST, random2);
      } else {
         session.createQueue(new QueueConfiguration(random1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         session.createQueue(new QueueConfiguration(random2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientConsumer consumer1 = session.createConsumer(random1);
      ClientConsumer consumer2 = session.createConsumer(random2);

      assertEquals(usingCore() ? 3 : 2, serverControl.getTotalConsumerCount());
      assertEquals(1, queueControl1.getConsumerCount());
      assertEquals(1, queueControl2.getConsumerCount());

      consumer1.close();
      consumer2.close();

      session.deleteQueue(random1);
      session.deleteQueue(random2);

      session.close();

      locator.close();
   }

   @Test
   public void testListConnectionsAsJSON() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      List<ClientSessionFactory> factories = new ArrayList<>();

      ServerLocator locator = createInVMNonHALocator();
      factories.add(createSessionFactory(locator));
      factories.add(createSessionFactory(locator));
      addClientSession(factories.get(1).createSession());

      String jsonString = serverControl.listConnectionsAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      Assert.assertEquals(usingCore() ? 3 : 2, array.size());

      JsonObject[] sorted = new JsonObject[array.size()];
      for (int i = 0; i < array.size(); i++) {
         sorted[i] = array.getJsonObject(i);
      }

      JsonObject first = null;
      JsonObject second = null;

      for (int i = 0; i < array.size(); i++) {
         JsonObject obj = array.getJsonObject(i);
         if (obj.getString("connectionID").equals(factories.get(0).getConnection().getID().toString())) {
            first = obj;
         }
         if (obj.getString("connectionID").equals(factories.get(1).getConnection().getID().toString())) {
            second = obj;
         }
      }

      Assert.assertNotNull(first);
      Assert.assertNotNull(second);

      Assert.assertTrue(first.getString("connectionID").length() > 0);
      Assert.assertTrue(first.getString("clientAddress").length() > 0);
      Assert.assertTrue(first.getJsonNumber("creationTime").longValue() > 0);
      Assert.assertEquals(0, first.getJsonNumber("sessionCount").longValue());

      Assert.assertTrue(second.getString("connectionID").length() > 0);
      Assert.assertTrue(second.getString("clientAddress").length() > 0);
      Assert.assertTrue(second.getJsonNumber("creationTime").longValue() > 0);
      Assert.assertEquals(1, second.getJsonNumber("sessionCount").longValue());
   }

   @Test
   public void testListConsumersAsJSON() throws Exception {
      SimpleString queueName = new SimpleString(UUID.randomUUID().toString());
      final String filter = "x = 1";
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session = addClientSession(factory.createSession());
      server.addAddressInfo(new AddressInfo(queueName, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }
      addClientConsumer(session.createConsumer(queueName));
      Thread.sleep(100); // We check the timestamp for the creation time. We need to make sure it's different
      addClientConsumer(session.createConsumer(queueName, SimpleString.toSimpleString(filter), true));

      String jsonString = serverControl.listConsumersAsJSON(factory.getConnection().getID().toString());
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      Assert.assertEquals(2, array.size());
      JsonObject first;
      JsonObject second;
      if (array.getJsonObject(0).getJsonNumber("creationTime").longValue() < array.getJsonObject(1).getJsonNumber("creationTime").longValue()) {
         first = array.getJsonObject(0);
         second = array.getJsonObject(1);
      } else {
         first = array.getJsonObject(1);
         second = array.getJsonObject(0);
      }

      Assert.assertNotNull(first.getJsonNumber(ConsumerField.ID.getAlternativeName()).longValue());
      Assert.assertTrue(first.getString(ConsumerField.CONNECTION.getAlternativeName()).length() > 0);
      Assert.assertEquals(factory.getConnection().getID().toString(), first.getString(ConsumerField.CONNECTION.getAlternativeName()));
      Assert.assertTrue(first.getString(ConsumerField.SESSION.getAlternativeName()).length() > 0);
      Assert.assertEquals(((ClientSessionImpl) session).getName(), first.getString(ConsumerField.SESSION.getAlternativeName()));
      Assert.assertTrue(first.getString(ConsumerField.QUEUE.getAlternativeName()).length() > 0);
      Assert.assertEquals(queueName.toString(), first.getString(ConsumerField.QUEUE.getAlternativeName()));
      Assert.assertEquals(false, first.getBoolean(ConsumerField.BROWSE_ONLY.getName()));
      Assert.assertTrue(first.getJsonNumber(ConsumerField.CREATION_TIME.getName()).longValue() > 0);
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      // test the old version that has been replaced for backward compatibility
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getAlternativeName()).longValue());

      Assert.assertNotNull(second.getJsonNumber(ConsumerField.ID.getAlternativeName()).longValue());
      Assert.assertTrue(second.getString(ConsumerField.CONNECTION.getAlternativeName()).length() > 0);
      Assert.assertEquals(factory.getConnection().getID().toString(), second.getString(ConsumerField.CONNECTION.getAlternativeName()));
      Assert.assertTrue(second.getString(ConsumerField.SESSION.getAlternativeName()).length() > 0);
      Assert.assertEquals(((ClientSessionImpl) session).getName(), second.getString(ConsumerField.SESSION.getAlternativeName()));
      Assert.assertTrue(second.getString(ConsumerField.QUEUE.getAlternativeName()).length() > 0);
      Assert.assertEquals(queueName.toString(), second.getString(ConsumerField.QUEUE.getAlternativeName()));
      Assert.assertEquals(true, second.getBoolean(ConsumerField.BROWSE_ONLY.getName()));
      Assert.assertTrue(second.getJsonNumber(ConsumerField.CREATION_TIME.getName()).longValue() > 0);
      Assert.assertEquals(0, second.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertTrue(second.getString(ConsumerField.FILTER.getName()).length() > 0);
      Assert.assertEquals(filter, second.getString(ConsumerField.FILTER.getName()));
   }

   @Test
   public void testListAllConsumersAsJSON() throws Exception {
      SimpleString queueName = new SimpleString(UUID.randomUUID().toString());
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session = addClientSession(factory.createSession());

      ServerLocator locator2 = createInVMNonHALocator();
      ClientSessionFactory factory2 = createSessionFactory(locator2);
      ClientSession session2 = addClientSession(factory2.createSession());
      serverControl.createAddress(queueName.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      addClientConsumer(session.createConsumer(queueName));
      Thread.sleep(200);
      addClientConsumer(session2.createConsumer(queueName));

      String jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      Assert.assertEquals(usingCore() ? 3 : 2, array.size());

      String key = "creationTime";
      JsonObject[] sorted = new JsonObject[array.size()];
      for (int i = 0; i < array.size(); i++) {
         sorted[i] = array.getJsonObject(i);
      }

      if (sorted[0].getJsonNumber(key).longValue() > sorted[1].getJsonNumber(key).longValue()) {
         JsonObject o = sorted[1];
         sorted[1] = sorted[0];
         sorted[0] = o;
      }
      if (usingCore()) {
         if (sorted[1].getJsonNumber(key).longValue() > sorted[2].getJsonNumber(key).longValue()) {
            JsonObject o = sorted[2];
            sorted[2] = sorted[1];
            sorted[1] = o;
         }
         if (sorted[0].getJsonNumber(key).longValue() > sorted[1].getJsonNumber(key).longValue()) {
            JsonObject o = sorted[1];
            sorted[1] = sorted[0];
            sorted[0] = o;
         }
      }

      JsonObject first = sorted[0];
      JsonObject second = sorted[1];

      Assert.assertTrue(first.getJsonNumber(ConsumerField.CREATION_TIME.getName()).longValue() > 0);
      Assert.assertNotNull(first.getJsonNumber(ConsumerField.ID.getAlternativeName()).longValue());
      Assert.assertTrue(first.getString(ConsumerField.CONNECTION.getAlternativeName()).length() > 0);
      Assert.assertEquals(factory.getConnection().getID().toString(), first.getString(ConsumerField.CONNECTION.getAlternativeName()));
      Assert.assertTrue(first.getString(ConsumerField.SESSION.getAlternativeName()).length() > 0);
      Assert.assertEquals(((ClientSessionImpl) session).getName(), first.getString(ConsumerField.SESSION.getAlternativeName()));
      Assert.assertTrue(first.getString(ConsumerField.QUEUE.getAlternativeName()).length() > 0);
      Assert.assertEquals(queueName.toString(), first.getString(ConsumerField.QUEUE.getAlternativeName()));
      Assert.assertEquals(false, first.getBoolean(ConsumerField.BROWSE_ONLY.getName()));
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.LAST_DELIVERED_TIME.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.LAST_ACKNOWLEDGED_TIME.getName()).longValue());

      Assert.assertTrue(second.getJsonNumber(ConsumerField.CREATION_TIME.getName()).longValue() > 0);
      Assert.assertNotNull(second.getJsonNumber(ConsumerField.ID.getAlternativeName()).longValue());
      Assert.assertTrue(second.getString(ConsumerField.CONNECTION.getAlternativeName()).length() > 0);
      Assert.assertEquals(factory2.getConnection().getID().toString(), second.getString(ConsumerField.CONNECTION.getAlternativeName()));
      Assert.assertTrue(second.getString(ConsumerField.SESSION.getAlternativeName()).length() > 0);
      Assert.assertEquals(((ClientSessionImpl) session2).getName(), second.getString(ConsumerField.SESSION.getAlternativeName()));
      Assert.assertTrue(second.getString(ConsumerField.QUEUE.getAlternativeName()).length() > 0);
      Assert.assertEquals(queueName.toString(), second.getString(ConsumerField.QUEUE.getAlternativeName()));
      Assert.assertEquals(false, second.getBoolean(ConsumerField.BROWSE_ONLY.getName()));
      Assert.assertEquals(0, second.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertEquals(0, second.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      Assert.assertEquals(0, second.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      Assert.assertEquals(0, second.getJsonNumber(ConsumerField.LAST_DELIVERED_TIME.getName()).longValue());
      Assert.assertEquals(0, second.getJsonNumber(ConsumerField.LAST_ACKNOWLEDGED_TIME.getName()).longValue());

   }


   @Test
   public void testListAllConsumersAsJSONTXCommit() throws Exception {
      SimpleString queueName = new SimpleString(UUID.randomUUID().toString());
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session = factory.createSession(true,false, 1);
      addClientSession(session);

      serverControl.createAddress(queueName.toString(), RoutingType.ANYCAST.name());
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientConsumer consumer = session.createConsumer(queueName, null, 100, -1, false);
      addClientConsumer(consumer);
      session.start();

      ClientProducer producer = session.createProducer(queueName);
      int size = 0;
      ClientMessage receive = null;
      for (int i = 0; i < 100; i++) {
         ClientMessage message = session.createMessage(true);

         producer.send(message);
         size += message.getEncodeSize();
         receive = consumer.receive();
      }

      String jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      JsonObject first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
      receive.acknowledge();
      session.commit();

      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());

      int allSize = size;
      for (int i = 0; i < 100; i++) {
         ClientMessage message = session.createMessage(true);

         producer.send(message);
         allSize += message.getEncodeSize();
         receive = consumer.receive();
      }

      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      Assert.assertEquals(200, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      Assert.assertEquals(allSize, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
   }

   @Test
   public void testListAllConsumersAsJSONTXCommitAck() throws Exception {
      SimpleString queueName = new SimpleString(UUID.randomUUID().toString());
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session = factory.createSession(true,false, 1);
      addClientSession(session);

      serverControl.createAddress(queueName.toString(), RoutingType.ANYCAST.name());
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientConsumer consumer = session.createConsumer(queueName, null, 100, -1, false);
      addClientConsumer(consumer);
      session.start();

      ClientProducer producer = session.createProducer(queueName);
      int size = 0;
      ClientMessage receive = null;
      for (int i = 0; i < 100; i++) {
         ClientMessage message = session.createMessage(true);

         producer.send(message);
         size += message.getEncodeSize();
         receive = consumer.receive();
         receive.acknowledge();
      }

      Wait.assertEquals(0, () -> JsonUtil.readJsonArray(serverControl.listAllConsumersAsJSON()).get(0).asJsonObject().getInt(ConsumerField.MESSAGES_IN_TRANSIT.getName()));

      String jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      JsonObject first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
      session.commit();

      Wait.assertEquals(0, () -> JsonUtil.readJsonArray(serverControl.listAllConsumersAsJSON()).get(0).asJsonObject().getInt(ConsumerField.MESSAGES_IN_TRANSIT.getName()));

      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
   }

   @Test
   public void testListAllConsumersAsJSONTXRollback() throws Exception {
      SimpleString queueName = new SimpleString(UUID.randomUUID().toString());
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session = factory.createSession(true,false, 1);
      addClientSession(session);

      serverControl.createAddress(queueName.toString(), RoutingType.ANYCAST.name());
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientConsumer consumer = session.createConsumer(queueName, null, 100, -1, false);
      addClientConsumer(consumer);
      session.start();

      ClientProducer producer = session.createProducer(queueName);
      int size = 0;
      ClientMessage receive = null;
      for (int i = 0; i < 100; i++) {
         ClientMessage message = session.createMessage(true);

         producer.send(message);
         size += message.getEncodeSize();
         receive = consumer.receive();
      }

      String jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      JsonObject first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
      receive.acknowledge();   //stop the session so we dont receive the same messages
      session.stop();
      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());

      session.rollback();
      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());

      int allSize = size * 2;
      session.start();
      for (int i = 0; i < 100; i++) {
         receive = consumer.receive();
      }

      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      Assert.assertEquals(200, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      Assert.assertEquals(allSize, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());

      receive.acknowledge();
      //stop the session so we dont receive the same messages
      session.stop();
      session.rollback();
      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      Assert.assertEquals(200, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      Assert.assertEquals(allSize, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      Assert.assertEquals(200, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
   }

   @Test
   public void testListAllConsumersAsJSONCancel() throws Exception {
      SimpleString queueName = new SimpleString(UUID.randomUUID().toString());
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session = factory.createSession(true,false, 1);
      addClientSession(session);

      serverControl.createAddress(queueName.toString(), RoutingType.ANYCAST.name());
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientConsumer consumer = session.createConsumer(queueName, null, 100, -1, false);
      addClientConsumer(consumer);
      session.start();

      ClientProducer producer = session.createProducer(queueName);
      int size = 0;
      ClientMessage receive = null;
      for (int i = 0; i < 100; i++) {
         ClientMessage message = session.createMessage(true);

         producer.send(message);
         size += message.getEncodeSize();
         receive = consumer.receive();
      }

      String jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      JsonObject first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
      receive.acknowledge();
      //close the consumer to cancel messages and recreate
      consumer.close();
      session.close();
      session = factory.createSession(true,false, 1);
      addClientSession(session);

      consumer = session.createConsumer(queueName, null, 100, -1, false);
      addClientConsumer(consumer);
      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
   }

   @Test
   public void testListAllConsumersAsJSONNoTX() throws Exception {
      SimpleString queueName = new SimpleString(UUID.randomUUID().toString());
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session = factory.createSession(true, true, 1);
      addClientSession(session);

      serverControl.createAddress(queueName.toString(), RoutingType.ANYCAST.name());
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientConsumer consumer = session.createConsumer(queueName, null, 100, -1, false);
      addClientConsumer(consumer);
      session.start();

      ClientProducer producer = session.createProducer(queueName);
      int size = 0;
      ClientMessage receive = null;
      for (int i = 0; i < 100; i++) {
         ClientMessage message = session.createMessage(true);

         producer.send(message);
         size += message.getEncodeSize();
         receive = consumer.receive();
         receive.acknowledge();
      }

      Wait.assertEquals(0, () -> JsonUtil.readJsonArray(serverControl.listAllConsumersAsJSON()).get(0).asJsonObject().getInt(ConsumerField.MESSAGES_IN_TRANSIT.getName()));

      String jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      JsonObject first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
   }

   @Test
   public void testListAllConsumersAsJSONXACommit() throws Exception {
      SimpleString queueName = new SimpleString(UUID.randomUUID().toString());
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession sendSession = factory.createSession();
      addClientSession(sendSession);
      ClientSession session = factory.createSession(true, false, false);
      addClientSession(session);

      serverControl.createAddress(queueName.toString(), RoutingType.ANYCAST.name());
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientConsumer consumer = session.createConsumer(queueName, null, 100, -1, false);
      addClientConsumer(consumer);
      session.start();
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      session.start(xid, XAResource.TMNOFLAGS);

      ClientProducer producer = sendSession.createProducer(queueName);
      int size = 0;
      ClientMessage receive = null;
      for (int i = 0; i < 100; i++) {
         ClientMessage message = sendSession.createMessage(true);

         producer.send(message);
         size += message.getEncodeSize();
         receive = consumer.receive();
         receive.acknowledge();
      }

      String jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      JsonObject first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());

      session.end(xid, XAResource.TMSUCCESS);
      session.prepare(xid);
      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
      session.commit(xid, false);
      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
   }

   @Test
   public void testListAllConsumersAsJSONXARollback() throws Exception {
      SimpleString queueName = new SimpleString(UUID.randomUUID().toString());
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession sendSession = factory.createSession();
      addClientSession(sendSession);
      ClientSession session = factory.createSession(true, false, false);
      addClientSession(session);

      serverControl.createAddress(queueName.toString(), RoutingType.ANYCAST.name());
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientConsumer consumer = session.createConsumer(queueName, null, 100, -1, false);
      addClientConsumer(consumer);
      session.start();
      Xid xid = new XidImpl("xa1".getBytes(), 1, UUIDGenerator.getInstance().generateStringUUID().getBytes());
      session.start(xid, XAResource.TMNOFLAGS);

      ClientProducer producer = sendSession.createProducer(queueName);
      int size = 0;
      ClientMessage receive = null;
      for (int i = 0; i < 100; i++) {
         ClientMessage message = sendSession.createMessage(true);

         producer.send(message);
         size += message.getEncodeSize();
         receive = consumer.receive();
         receive.acknowledge();
      }

      String jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      JsonObject first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());

      session.end(xid, XAResource.TMSUCCESS);
      session.prepare(xid);
      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
      session.stop();
      session.rollback(xid);
      jsonString = serverControl.listAllConsumersAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      array = JsonUtil.readJsonArray(jsonString);
      first = (JsonObject) array.get(0);
      if (!first.getString(ConsumerField.QUEUE.getAlternativeName()).equalsIgnoreCase(queueName.toString())) {
         first = (JsonObject) array.get(1);
      }
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED.getName()).longValue());
      Assert.assertEquals(size, first.getJsonNumber(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()).longValue());
      Assert.assertEquals(100, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()).longValue());
      Assert.assertEquals(0, first.getJsonNumber(ConsumerField.MESSAGES_ACKNOWLEDGED_AWAITING_COMMIT.getName()).longValue());
   }

   @Test
   public void testListSessionsAsJSON() throws Exception {
      SimpleString queueName = new SimpleString(UUID.randomUUID().toString());
      server.addAddressInfo(new AddressInfo(queueName, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ClientSession session1 = addClientSession(factory.createSession());
      ClientSession session2 = addClientSession(factory.createSession("myUser", "myPass", false, false, false, false, 0));
      session2.createConsumer(queueName);

      String jsonString = serverControl.listSessionsAsJSON(factory.getConnection().getID().toString());
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      Assert.assertEquals(2, array.size());
      JsonObject first = lookupSession(array, session1);
      JsonObject second = lookupSession(array, session2);

      Assert.assertTrue(first.getString("sessionID").length() > 0);
      Assert.assertEquals(((ClientSessionImpl) session1).getName(), first.getString("sessionID"));
      Assert.assertTrue(first.getString("principal").length() > 0);
      Assert.assertEquals("guest", first.getString("principal"));
      Assert.assertTrue(first.getJsonNumber("creationTime").longValue() > 0);
      Assert.assertEquals(0, first.getJsonNumber("consumerCount").longValue());

      Assert.assertTrue(second.getString("sessionID").length() > 0);
      Assert.assertEquals(((ClientSessionImpl) session2).getName(), second.getString("sessionID"));
      Assert.assertTrue(second.getString("principal").length() > 0);
      Assert.assertEquals("myUser", second.getString("principal"));
      Assert.assertTrue(second.getJsonNumber("creationTime").longValue() > 0);
      Assert.assertEquals(1, second.getJsonNumber("consumerCount").longValue());
   }

   private JsonObject lookupSession(JsonArray jsonArray, ClientSession session) throws Exception {
      String name = ((ClientSessionImpl)session).getName();

      for (int i = 0; i < jsonArray.size(); i++) {
         JsonObject obj = jsonArray.getJsonObject(i);
         String sessionID = obj.getString("sessionID");
         Assert.assertNotNull(sessionID);

         if (sessionID.equals(name)) {
            return obj;
         }
      }

      Assert.fail("Sesison not found for session id " + name);

      // not going to happen, fail will throw an exception but it won't compile without this
      return null;
   }

   @Test
   public void testListAllSessionsAsJSON() throws Exception {
      SimpleString queueName = new SimpleString(UUID.randomUUID().toString());
      server.addAddressInfo(new AddressInfo(queueName, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }
      ActiveMQServerControl serverControl = createManagementControl();

      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory factory = createSessionFactory(locator);
      ServerLocator locator2 = createInVMNonHALocator();
      ClientSessionFactory factory2 = createSessionFactory(locator2);
      ClientSession session1 = addClientSession(factory.createSession());
      Thread.sleep(5);
      ClientSession session2 = addClientSession(factory2.createSession("myUser", "myPass", false, false, false, false, 0));
      session2.addMetaData("foo", "bar");
      session2.addMetaData("bar", "baz");
      session2.createConsumer(queueName);

      String jsonString = serverControl.listAllSessionsAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      Assert.assertEquals(2 + (usingCore() ? 1 : 0), array.size());
      JsonObject first = lookupSession(array, session1);
      JsonObject second = lookupSession(array, session2);

      Assert.assertTrue(first.getString("sessionID").length() > 0);
      Assert.assertEquals(((ClientSessionImpl) session1).getName(), first.getString("sessionID"));
      Assert.assertTrue(first.getString("principal").length() > 0);
      Assert.assertEquals("guest", first.getString("principal"));
      Assert.assertTrue(first.getJsonNumber("creationTime").longValue() > 0);
      Assert.assertEquals(0, first.getJsonNumber("consumerCount").longValue());

      Assert.assertTrue(second.getString("sessionID").length() > 0);
      Assert.assertEquals(((ClientSessionImpl) session2).getName(), second.getString("sessionID"));
      Assert.assertTrue(second.getString("principal").length() > 0);
      Assert.assertEquals("myUser", second.getString("principal"));
      Assert.assertTrue(second.getJsonNumber("creationTime").longValue() > 0);
      Assert.assertEquals(1, second.getJsonNumber("consumerCount").longValue());
      Assert.assertEquals(second.getJsonObject("metadata").getJsonString("foo").getString(), "bar");
      Assert.assertEquals(second.getJsonObject("metadata").getJsonString("bar").getString(), "baz");
   }

   @Test
   public void testListAllSessionsAsJSONWithJMS() throws Exception {
      SimpleString queueName = new SimpleString(UUID.randomUUID().toString());
      server.addAddressInfo(new AddressInfo(queueName, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName, RoutingType.ANYCAST, queueName, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }
      ActiveMQServerControl serverControl = createManagementControl();

      ActiveMQConnectionFactory cf = ActiveMQJMSClient.createConnectionFactory("vm://0", "cf");
      Connection con = cf.createConnection();
      String clientID = UUID.randomUUID().toString();
      con.setClientID(clientID);

      String jsonString = serverControl.listAllSessionsAsJSON();
      logger.debug(jsonString);
      Assert.assertNotNull(jsonString);
      JsonArray array = JsonUtil.readJsonArray(jsonString);
      Assert.assertEquals(1 + (usingCore() ? 1 : 0), array.size());
      JsonObject obj = lookupSession(array, ((ActiveMQConnection)con).getInitialSession());
      Assert.assertEquals(obj.getJsonObject("metadata").getJsonString(ClientSession.JMS_SESSION_CLIENT_ID_PROPERTY).getString(), clientID);
      Assert.assertNotNull(obj.getJsonObject("metadata").getJsonString(ClientSession.JMS_SESSION_IDENTIFIER_PROPERTY));
   }

   @Test
   public void testListQueues() throws Exception {
      SimpleString queueName1 = new SimpleString("my_queue_one");
      SimpleString queueName2 = new SimpleString("my_queue_two");
      SimpleString queueName3 = new SimpleString("other_queue_three");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(queueName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      server.addAddressInfo(new AddressInfo(queueName2, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName2, RoutingType.ANYCAST, queueName2, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      server.addAddressInfo(new AddressInfo(queueName3, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName3, RoutingType.ANYCAST, queueName3, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName3).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      //test with filter that matches 2 queues
      String filterString = createJsonFilter("name", "CONTAINS", "my_queue");

      String queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

      JsonObject queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      JsonArray array = (JsonArray) queuesAsJsonObject.get("data");

      Assert.assertEquals("number of queues returned from query", 2, array.size());
      Assert.assertTrue(array.getJsonObject(0).getString("name").contains("my_queue"));
      Assert.assertTrue(array.getJsonObject(1).getString("name").contains("my_queue"));

      //test with an empty filter
      filterString = createJsonFilter("", "", "");

      queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

      queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      array = (JsonArray) queuesAsJsonObject.get("data");

      // at least 3 queues or more
      Assert.assertTrue("number of queues returned from query", 3 <= array.size());

      //test with small page size
      queuesAsJsonString = serverControl.listQueues(filterString, 1, 1);

      queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      array = (JsonArray) queuesAsJsonObject.get("data");

      Assert.assertEquals("number of queues returned from query", 1, array.size());
      //check all field names are available
      Assert.assertNotEquals("name", "", array.getJsonObject(0).getString("name"));
      Assert.assertNotEquals("id", "", array.getJsonObject(0).getString("id"));
      Assert.assertNotEquals("address", "", array.getJsonObject(0).getString("address"));
      Assert.assertEquals("filter", "", array.getJsonObject(0).getString("filter"));
      Assert.assertEquals("durable", "false", array.getJsonObject(0).getString("durable"));
      Assert.assertEquals("paused", "false", array.getJsonObject(0).getString("paused"));
      Assert.assertNotEquals("temporary", "", array.getJsonObject(0).getString("temporary"));
      Assert.assertEquals("purgeOnNoConsumers", "false", array.getJsonObject(0).getString("purgeOnNoConsumers"));
      Assert.assertNotEquals("consumerCount", "", array.getJsonObject(0).getString("consumerCount"));
      Assert.assertEquals("maxConsumers", "-1", array.getJsonObject(0).getString("maxConsumers"));
      Assert.assertEquals("autoCreated", "false", array.getJsonObject(0).getString("autoCreated"));
      Assert.assertEquals("user", usingCore() ? "guest" : "", array.getJsonObject(0).getString("user"));
      Assert.assertNotEquals("routingType", "", array.getJsonObject(0).getString("routingType"));
      Assert.assertEquals("messagesAdded", "0", array.getJsonObject(0).getString("messagesAdded"));
      Assert.assertEquals("messageCount", "0", array.getJsonObject(0).getString("messageCount"));
      Assert.assertEquals("messagesAcked", "0", array.getJsonObject(0).getString("messagesAcked"));
      Assert.assertEquals("deliveringCount", "0", array.getJsonObject(0).getString("deliveringCount"));
      Assert.assertEquals("messagesKilled", "0", array.getJsonObject(0).getString("messagesKilled"));
      Assert.assertEquals("exclusive", "false", array.getJsonObject(0).getString("exclusive"));
      Assert.assertEquals("lastValue", "false", array.getJsonObject(0).getString("lastValue"));
      Assert.assertEquals("scheduledCount", "0", array.getJsonObject(0).getString("scheduledCount"));
      Assert.assertEquals("groupRebalance", "false", array.getJsonObject(0).getString("groupRebalance"));
      Assert.assertEquals("groupBuckets", "-1", array.getJsonObject(0).getString("groupBuckets"));
      Assert.assertEquals("groupFirstKey", "", array.getJsonObject(0).getString("groupFirstKey"));
      Assert.assertEquals("autoDelete", "false", array.getJsonObject(0).getString("autoDelete"));
   }

   @Test
   public void testListQueuesOrder() throws Exception {
      SimpleString queueName1 = new SimpleString("my_queue_1");
      SimpleString queueName2 = new SimpleString("my_queue_2");
      SimpleString queueName3 = new SimpleString("my_queue_3");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(queueName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName1, RoutingType.ANYCAST, queueName1, new SimpleString("filter1"),null,true,
                            false, false,20,false,false);
      } else {
         server.createQueue(new QueueConfiguration(queueName1).setRoutingType(RoutingType.ANYCAST).setFilterString("filter1").setMaxConsumers(20).setAutoCreateAddress(false));
      }
      Thread.sleep(500);
      server.addAddressInfo(new AddressInfo(queueName2, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName2, RoutingType.ANYCAST, queueName2, new SimpleString("filter3"), null,true,
                            false, true,40,false,false);
      } else {
         server.createQueue(new QueueConfiguration(queueName2).setRoutingType(RoutingType.ANYCAST).setFilterString("filter3").setAutoCreated(true).setMaxConsumers(40).setAutoCreateAddress(false));
      }
      Thread.sleep(500);
      server.addAddressInfo(new AddressInfo(queueName3, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName3, RoutingType.ANYCAST, queueName3,  new SimpleString("filter0"),null,true,
                            false, false,10,false,false);
      } else {
         server.createQueue(new QueueConfiguration(queueName3).setRoutingType(RoutingType.ANYCAST).setFilterString("filter0").setMaxConsumers(10).setAutoCreateAddress(false));
      }

      //test default order
      String filterString = createJsonFilter("name", "CONTAINS", "my_queue");
      String queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);
      JsonObject queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      JsonArray array = (JsonArray) queuesAsJsonObject.get("data");

      Assert.assertEquals("number of queues returned from query", 3, array.size());
      Assert.assertEquals("queue1 default Order", queueName1.toString(), array.getJsonObject(0).getString("name"));
      Assert.assertEquals("queue2 default Order", queueName2.toString(), array.getJsonObject(1).getString("name"));
      Assert.assertEquals("queue3 default Order", queueName3.toString(), array.getJsonObject(2).getString("name"));

      //test ordered by id desc
      filterString = createJsonFilter("name", "CONTAINS", "my_queue", "id", "desc");
      queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);
      queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      array = (JsonArray) queuesAsJsonObject.get("data");

      Assert.assertEquals("number of queues returned from query", 3, array.size());
      Assert.assertEquals("queue3 ordered by id", queueName3.toString(), array.getJsonObject(0).getString("name"));
      Assert.assertEquals("queue2 ordered by id", queueName2.toString(), array.getJsonObject(1).getString("name"));
      Assert.assertEquals("queue1 ordered by id", queueName1.toString(), array.getJsonObject(2).getString("name"));

      //ordered by address desc
      filterString = createJsonFilter("name", "CONTAINS", "my_queue", "address", "desc");
      queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);
      queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      array = (JsonArray) queuesAsJsonObject.get("data");

      Assert.assertEquals("number of queues returned from query", 3, array.size());
      Assert.assertEquals("queue3 ordered by address", queueName3.toString(), array.getJsonObject(0).getString("name"));
      Assert.assertEquals("queue2 ordered by address", queueName2.toString(), array.getJsonObject(1).getString("name"));
      Assert.assertEquals("queue1 ordered by address", queueName1.toString(), array.getJsonObject(2).getString("name"));

      //ordered by auto create desc
      filterString = createJsonFilter("name", "CONTAINS", "my_queue", "autoCreated", "asc");
      queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);
      queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      array = (JsonArray) queuesAsJsonObject.get("data");

      Assert.assertEquals("number of queues returned from query", 3, array.size());
      Assert.assertEquals("pos1 ordered by autocreate", "false", array.getJsonObject(0).getString("autoCreated"));
      Assert.assertEquals("pos2 ordered by autocreate", "false", array.getJsonObject(1).getString("autoCreated"));
      Assert.assertEquals("pos3 ordered by autocreate", "true", array.getJsonObject(2).getString("autoCreated"));

      //ordered by filter desc
      filterString = createJsonFilter("name", "CONTAINS", "my_queue", "filter", "desc");
      queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);
      queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      array = (JsonArray) queuesAsJsonObject.get("data");

      Assert.assertEquals("number of queues returned from query", 3, array.size());
      Assert.assertEquals("queue2 ordered by filter", queueName2.toString(), array.getJsonObject(0).getString("name"));
      Assert.assertEquals("queue1 ordered by filter", queueName1.toString(), array.getJsonObject(1).getString("name"));
      Assert.assertEquals("queue3 ordered by filter", queueName3.toString(), array.getJsonObject(2).getString("name"));

      //ordered by max consumers asc
      filterString = createJsonFilter("name", "CONTAINS", "my_queue", "maxConsumers", "asc");
      queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);
      queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      array = (JsonArray) queuesAsJsonObject.get("data");

      Assert.assertEquals("number of queues returned from query", 3, array.size());
      Assert.assertEquals("queue3 ordered by filter", queueName3.toString(), array.getJsonObject(0).getString("name"));
      Assert.assertEquals("queue1 ordered by filter", queueName1.toString(), array.getJsonObject(1).getString("name"));
      Assert.assertEquals("queue2 ordered by filter", queueName2.toString(), array.getJsonObject(2).getString("name"));

      //ordering between the pages
      //page 1
      filterString = createJsonFilter("name", "CONTAINS", "my_queue", "address", "desc");
      queuesAsJsonString = serverControl.listQueues(filterString, 1, 1);
      queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      array = (JsonArray) queuesAsJsonObject.get("data");
      Assert.assertEquals("number of queues returned from query", 1, array.size());
      Assert.assertEquals("queue3 ordered by page", queueName3.toString(), array.getJsonObject(0).getString("name"));

      //page 2
      filterString = createJsonFilter("name", "CONTAINS", "my_queue", "address", "desc");
      queuesAsJsonString = serverControl.listQueues(filterString, 2, 1);
      queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      array = (JsonArray) queuesAsJsonObject.get("data");
      Assert.assertEquals("number of queues returned from query", 1, array.size());
      Assert.assertEquals("queue2 ordered by page", queueName2.toString(), array.getJsonObject(0).getString("name"));

      //page 3
      filterString = createJsonFilter("name", "CONTAINS", "my_queue", "address", "desc");
      queuesAsJsonString = serverControl.listQueues(filterString, 3, 1);
      queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
      array = (JsonArray) queuesAsJsonObject.get("data");
      Assert.assertEquals("number of queues returned from query", 1, array.size());
      Assert.assertEquals("queue1 ordered by page", queueName1.toString(), array.getJsonObject(0).getString("name"));

   }

   @Test
   public void testListQueuesNumericFilter() throws Exception {
      SimpleString queueName1 = new SimpleString("my_queue_one");
      SimpleString queueName2 = new SimpleString("my_queue_two");
      SimpleString queueName3 = new SimpleString("one_consumer_queue_three");
      SimpleString queueName4 = new SimpleString("my_queue_four");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(queueName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      server.addAddressInfo(new AddressInfo(queueName2, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName2, RoutingType.ANYCAST, queueName2, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      server.addAddressInfo(new AddressInfo(queueName3, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName3, RoutingType.ANYCAST, queueName3, null, false, false, 10, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName3).setRoutingType(RoutingType.ANYCAST).setDurable(false).setMaxConsumers(10));
      }

      server.addAddressInfo(new AddressInfo(queueName4, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName4, RoutingType.ANYCAST, queueName4, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName4).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator);
           ClientSession session = csf.createSession()) {

         session.start();
         ClientConsumer consumer1_q1 = session.createConsumer(queueName1);
         ClientConsumer consumer2_q1 = session.createConsumer(queueName1);
         ClientConsumer consumer1_q2 = session.createConsumer(queueName2);
         ClientConsumer consumer2_q2 = session.createConsumer(queueName2);
         ClientConsumer consumer3_q2 = session.createConsumer(queueName2);
         ClientConsumer consumer1_q3 = session.createConsumer(queueName3);

         ClientProducer clientProducer = session.createProducer(queueName1);
         ClientMessage message = session.createMessage(false);
         for (int i = 0; i < 10; i++) {
            clientProducer.send(message);
         }

         //consume one message
         ClientMessage messageReceived = consumer1_q1.receive(100);
         if (messageReceived == null) {
            fail("should have received a message");
         }
         messageReceived.acknowledge();
         session.commit();

         //test with CONTAINS returns nothing for numeric field
         String filterString = createJsonFilter("CONSUMER_COUNT", "CONTAINS", "0");
         String queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         JsonObject queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         JsonArray array = (JsonArray) queuesAsJsonObject.get("data");
         Assert.assertEquals("number of queues returned from query", 0, array.size());

         //test with LESS_THAN returns 1 queue
         filterString = createJsonFilter("CONSUMER_COUNT", "LESS_THAN", "1");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         Assert.assertEquals("number of queues returned from LESS_THAN query", 1, array.size());
         Assert.assertEquals("correct queue returned from query", queueName4.toString(), array.getJsonObject(0).getString("name"));

         //test with GREATER_THAN returns 2 queue
         filterString = createJsonFilter("CONSUMER_COUNT", "GREATER_THAN", "2");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         Assert.assertEquals("number of queues returned from GREATER_THAN query", 1, array.size());
         Assert.assertEquals("correct queue returned from query", queueName2.toString(), array.getJsonObject(0).getString("name"));

         //test with GREATER_THAN returns 2 queue
         filterString = createJsonFilter("CONSUMER_COUNT", "EQUALS", "3");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         Assert.assertEquals("number of queues returned from EQUALS query", 1, array.size());
         Assert.assertEquals("correct queue returned from query", queueName2.toString(), array.getJsonObject(0).getString("name"));

         //test with MESSAGE_COUNT returns 2 queue
         filterString = createJsonFilter("MESSAGE_COUNT", "GREATER_THAN", "5");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         Assert.assertEquals("number of queues returned from MESSAGE_COUNT query", 1, array.size());
         Assert.assertEquals("correct queue returned from query", queueName1.toString(), array.getJsonObject(0).getString("name"));

         //test with MESSAGE_ADDED returns 1 queue
         filterString = createJsonFilter("MESSAGES_ADDED", "GREATER_THAN", "5");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         Assert.assertEquals("number of queues returned from MESSAGE_COUNT query", 1, array.size());
         Assert.assertEquals("correct queue returned from query", queueName1.toString(), array.getJsonObject(0).getString("name"));

         //test with DELIVERING_COUNT returns 1 queue
         filterString = createJsonFilter("DELIVERING_COUNT", "GREATER_THAN", "5");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         Assert.assertEquals("number of queues returned from DELIVERING_COUNT query", 1, array.size());
         Assert.assertEquals("correct queue returned from query", queueName1.toString(), array.getJsonObject(0).getString("name"));

         //test with MESSAGE_ACKED returns 1 queue
         filterString = createJsonFilter("MESSAGES_ACKED", "GREATER_THAN", "0");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         Assert.assertEquals("number of queues returned from MESSAGES_ACKED query", 1, array.size());
         Assert.assertEquals("correct queue returned from query", queueName1.toString(), array.getJsonObject(0).getString("name"));

         //test with MAX_CONSUMERS returns 1 queue
         filterString = createJsonFilter("MAX_CONSUMERS", "GREATER_THAN", "9");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         Assert.assertEquals("number of queues returned from MAX_CONSUMERS query", 1, array.size());
         Assert.assertEquals("correct queue returned from query", queueName3.toString(), array.getJsonObject(0).getString("name"));

         //test with MESSAGES_KILLED returns 0 queue
         filterString = createJsonFilter("MESSAGES_KILLED", "GREATER_THAN", "0");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         Assert.assertEquals("number of queues returned from MESSAGES_KILLED query", 0, array.size());

      }

   }

   @Test
   public void testListQueuesNumericFilterInvalid() throws Exception {
      SimpleString queueName1 = new SimpleString("my_queue_one");
      SimpleString queueName2 = new SimpleString("one_consumer_queue_two");
      SimpleString queueName3 = new SimpleString("one_consumer_queue_three");
      SimpleString queueName4 = new SimpleString("my_queue_four");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(queueName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      server.addAddressInfo(new AddressInfo(queueName2, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName2, RoutingType.ANYCAST, queueName2, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      server.addAddressInfo(new AddressInfo(queueName3, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName3, RoutingType.ANYCAST, queueName3, null, false, false, 10, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName3).setRoutingType(RoutingType.ANYCAST).setDurable(false).setMaxConsumers(10));
      }

      server.addAddressInfo(new AddressInfo(queueName4, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(queueName4, RoutingType.ANYCAST, queueName4, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName4).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator);
           ClientSession session = csf.createSession()) {

         session.start();
         ClientConsumer consumer1_q1 = session.createConsumer(queueName1);
         ClientConsumer consumer2_q1 = session.createConsumer(queueName1);

         //test with CONTAINS returns nothing for numeric field
         String filterString = createJsonFilter("CONSUMER_COUNT", "CONTAINS", "NOT_NUMBER");
         String queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         JsonObject queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         JsonArray array = (JsonArray) queuesAsJsonObject.get("data");
         Assert.assertEquals("number of queues returned from query", 0, array.size());

         //test with LESS_THAN and not a number
         filterString = createJsonFilter("CONSUMER_COUNT", "LESS_THAN", "NOT_NUMBER");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         Assert.assertEquals("number of queues returned from LESS_THAN query", 0, array.size());

         //test with GREATER_THAN and not a number
         filterString = createJsonFilter("CONSUMER_COUNT", "GREATER_THAN", "NOT_NUMBER");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         Assert.assertEquals("number of queues returned from GREATER_THAN query", 0, array.size());

         //test with EQUALS and not number
         filterString = createJsonFilter("CONSUMER_COUNT", "EQUALS", "NOT_NUMBER");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         Assert.assertEquals("number of queues returned from EQUALS query", 0, array.size());

         //test with LESS_THAN on string value returns no queue
         filterString = createJsonFilter("name", "LESS_THAN", "my_queue");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         Assert.assertEquals("number of queues returned from LESS_THAN on non numeric field", 0, array.size());

         //test with GREATER_THAN on string value returns no queue
         filterString = createJsonFilter("name", "GREATER_THAN", "my_queue");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         Assert.assertEquals("number of queues returned from GREATER_THAN on non numeric field", 0, array.size());

         //test with GREATER_THAN and empty string
         filterString = createJsonFilter("CONSUMER_COUNT", "GREATER_THAN", " ");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         Assert.assertEquals("number of queues returned from GREATER_THAN query", 0, array.size());

         //test with CONSUMER_COUNT against a float value
         filterString = createJsonFilter("CONSUMER_COUNT", "GREATER_THAN", "0.12");
         queuesAsJsonString = serverControl.listQueues(filterString, 1, 50);

         queuesAsJsonObject = JsonUtil.readJsonObject(queuesAsJsonString);
         array = (JsonArray) queuesAsJsonObject.get("data");
         Assert.assertEquals("number of queues returned from GREATER_THAN query", 0, array.size());

      }

   }

   @Test
   public void testListAddresses() throws Exception {
      SimpleString queueName1 = new SimpleString("my_queue_one");
      SimpleString queueName2 = new SimpleString("my_queue_two");
      SimpleString queueName3 = new SimpleString("other_queue_three");
      SimpleString queueName4 = new SimpleString("other_queue_four");

      SimpleString addressName1 = new SimpleString("my_address_one");
      SimpleString addressName2 = new SimpleString("my_address_two");
      SimpleString addressName3 = new SimpleString("other_address_three");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }
      server.addAddressInfo(new AddressInfo(addressName2, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName2, RoutingType.ANYCAST, queueName2, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName2).setAddress(addressName2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }
      server.addAddressInfo(new AddressInfo(addressName3, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName3, RoutingType.ANYCAST, queueName3, null, false, false);
         server.createQueue(addressName3, RoutingType.ANYCAST, queueName4, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName3).setAddress(addressName3).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         server.createQueue(new QueueConfiguration(queueName4).setAddress(addressName3).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      //test with CONTAINS filter
      String filterString = createJsonFilter("name", "CONTAINS", "my_address");
      String addressesAsJsonString = serverControl.listAddresses(filterString, 1, 50);
      JsonObject addressesAsJsonObject = JsonUtil.readJsonObject(addressesAsJsonString);
      JsonArray array = (JsonArray) addressesAsJsonObject.get("data");

      Assert.assertEquals("number of addresses returned from query", 2, array.size());
      Assert.assertTrue("address name check", array.getJsonObject(0).getString("name").contains("my_address"));
      Assert.assertTrue("address name check", array.getJsonObject(1).getString("name").contains("my_address"));

      //test with EQUALS filter
      filterString = createJsonFilter("name", "EQUALS", addressName1.toString());
      addressesAsJsonString = serverControl.listAddresses(filterString, 1, 50);
      addressesAsJsonObject = JsonUtil.readJsonObject(addressesAsJsonString);
      array = (JsonArray) addressesAsJsonObject.get("data");

      Assert.assertEquals("number of addresses returned from query", 1, array.size());
      //check all field names
      Assert.assertEquals("address name check", addressName1.toString(), array.getJsonObject(0).getString("name"));
      Assert.assertNotEquals("id", "", array.getJsonObject(0).getString("id"));
      Assert.assertTrue("routingTypes", array.getJsonObject(0).getString("routingTypes").contains(RoutingType.ANYCAST.name()));
      Assert.assertEquals("queueCount", "1", array.getJsonObject(0).getString("queueCount"));

      //test with empty filter - all addresses should be returned
      filterString = createJsonFilter("", "", "");
      addressesAsJsonString = serverControl.listAddresses(filterString, 1, 50);
      addressesAsJsonObject = JsonUtil.readJsonObject(addressesAsJsonString);
      array = (JsonArray) addressesAsJsonObject.get("data");

      Assert.assertTrue("number of addresses returned from query", 3 <= array.size());

      //test with small page size
      addressesAsJsonString = serverControl.listAddresses(filterString, 1, 1);
      addressesAsJsonObject = JsonUtil.readJsonObject(addressesAsJsonString);
      array = (JsonArray) addressesAsJsonObject.get("data");

      Assert.assertEquals("number of queues returned from query", 1, array.size());

      //test with QUEUE_COUNT with GREATER_THAN filter
      filterString = createJsonFilter("QUEUE_COUNT", "GREATER_THAN", "1");
      addressesAsJsonString = serverControl.listAddresses(filterString, 1, 50);
      addressesAsJsonObject = JsonUtil.readJsonObject(addressesAsJsonString);
      array = (JsonArray) addressesAsJsonObject.get("data");

      Assert.assertEquals("number of addresses returned from query", 1, array.size());
      Assert.assertEquals("address name check", addressName3.toString(), array.getJsonObject(0).getString("name"));

      //test with QUEUE_COUNT with LESS_THAN filter
      filterString = createJsonFilter("QUEUE_COUNT", "LESS_THAN", "0");
      addressesAsJsonString = serverControl.listAddresses(filterString, 1, 50);
      addressesAsJsonObject = JsonUtil.readJsonObject(addressesAsJsonString);
      array = (JsonArray) addressesAsJsonObject.get("data");

      Assert.assertEquals("number of addresses returned from query", 0, array.size());

   }

   @Test
   public void testListAddressOrder() throws Exception {

      SimpleString queueName1 = new SimpleString("my_queue_one");
      SimpleString queueName2 = new SimpleString("my_queue_two");
      SimpleString queueName3 = new SimpleString("other_queue_three");
      SimpleString queueName4 = new SimpleString("other_queue_four");

      SimpleString addressName1 = new SimpleString("my_address_1");
      SimpleString addressName2 = new SimpleString("my_address_2");
      SimpleString addressName3 = new SimpleString("my_address_3");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }
      server.addAddressInfo(new AddressInfo(addressName2, RoutingType.ANYCAST));
      server.addAddressInfo(new AddressInfo(addressName3, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName3, RoutingType.ANYCAST, queueName3, null, false, false);
         server.createQueue(addressName3, RoutingType.ANYCAST, queueName4, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName3).setAddress(addressName3).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         server.createQueue(new QueueConfiguration(queueName4).setAddress(addressName3).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      //test default order
      String filterString = createJsonFilter("name", "CONTAINS", "my");
      String addressesAsJsonString = serverControl.listAddresses(filterString, 1, 50);
      JsonObject addressesAsJsonObject = JsonUtil.readJsonObject(addressesAsJsonString);
      JsonArray array = (JsonArray) addressesAsJsonObject.get("data");

      Assert.assertEquals("number addresses returned", 3, array.size());
      Assert.assertEquals("address1 default order", addressName1.toString(), array.getJsonObject(0).getString("name"));
      Assert.assertEquals("address2 default order", addressName2.toString(), array.getJsonObject(1).getString("name"));
      Assert.assertEquals("address3 default order", addressName3.toString(), array.getJsonObject(2).getString("name"));

      //test  ordered by name desc
      filterString = createJsonFilter("name", "CONTAINS", "my", "name", "desc");
      addressesAsJsonString = serverControl.listAddresses(filterString, 1, 50);
      addressesAsJsonObject = JsonUtil.readJsonObject(addressesAsJsonString);
      array = (JsonArray) addressesAsJsonObject.get("data");

      Assert.assertEquals("number addresses returned", 3, array.size());
      Assert.assertEquals("address3 ordered by name", addressName3.toString(), array.getJsonObject(0).getString("name"));
      Assert.assertEquals("address2 ordered by name", addressName2.toString(), array.getJsonObject(1).getString("name"));
      Assert.assertEquals("address1 ordered by name", addressName1.toString(), array.getJsonObject(2).getString("name"));

      //test  ordered by queue count asc
      filterString = createJsonFilter("name", "CONTAINS", "my", "queueCount", "asc");
      addressesAsJsonString = serverControl.listAddresses(filterString, 1, 50);
      addressesAsJsonObject = JsonUtil.readJsonObject(addressesAsJsonString);
      array = (JsonArray) addressesAsJsonObject.get("data");

      Assert.assertEquals("number addresses returned", 3, array.size());
      Assert.assertEquals("address2 ordered by queue count", addressName2.toString(), array.getJsonObject(0).getString("name"));
      Assert.assertEquals("address1 ordered by queue count", addressName1.toString(), array.getJsonObject(1).getString("name"));
      Assert.assertEquals("address3 ordered by queue count", addressName3.toString(), array.getJsonObject(2).getString("name"));
   }

   @Test
   public void testListConsumers() throws Exception {
      SimpleString queueName1 = new SimpleString("my_queue_one");
      SimpleString queueName2 = new SimpleString("my_queue_two");
      SimpleString queueName3 = new SimpleString("other_queue_three");
      SimpleString addressName1 = new SimpleString("my_address_one");
      SimpleString addressName2 = new SimpleString("my_address_two");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      server.addAddressInfo(new AddressInfo(addressName2, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName2, RoutingType.ANYCAST, queueName2, null, false, false);
         server.createQueue(addressName2, RoutingType.ANYCAST, queueName3, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName2).setAddress(addressName2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         server.createQueue(new QueueConfiguration(queueName3).setAddress(addressName2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator);
           ClientSession session = csf.createSession()) {

         ClientConsumer consumer1_q1 = session.createConsumer(queueName1);
         ClientConsumer consumer2_q1 = session.createConsumer(queueName1);
         ClientConsumer consumer1_q2 = session.createConsumer(queueName2);
         ClientConsumer consumer1_q3 = session.createConsumer(queueName3);

         //test with filter  EQUALS
         String filterString = createJsonFilter("queue", "EQUALS", queueName1.toString());
         String consumersAsJsonString = serverControl.listConsumers(filterString, 1, 50);
         JsonObject consumersAsJsonObject = JsonUtil.readJsonObject(consumersAsJsonString);
         JsonArray array = (JsonArray) consumersAsJsonObject.get("data");

         Assert.assertEquals("number of consumers returned from query", 2, array.size());
         Assert.assertEquals("check consumer's queue", queueName1.toString(), array.getJsonObject(0).getString(ConsumerField.QUEUE.getName()));
         Assert.assertEquals("check consumer's queue", queueName1.toString(), array.getJsonObject(0).getString(ConsumerField.QUEUE.getName()));

         // test with a CONTAINS operation
         filterString = createJsonFilter(ConsumerField.QUEUE.getName(), "CONTAINS", "my_queue");
         consumersAsJsonString = serverControl.listConsumers(filterString, 1, 50);
         consumersAsJsonObject = JsonUtil.readJsonObject(consumersAsJsonString);
         array = (JsonArray) consumersAsJsonObject.get("data");

         Assert.assertEquals("number of consumers returned from query", 3, array.size());

         // filter by address
         filterString = createJsonFilter(ConsumerField.ADDRESS.getName(),  "EQUALS", addressName1.toString());
         consumersAsJsonString = serverControl.listConsumers(filterString, 1, 50);
         consumersAsJsonObject = JsonUtil.readJsonObject(consumersAsJsonString);
         array = (JsonArray) consumersAsJsonObject.get("data");

         Assert.assertEquals("number of consumers returned from query", 2, array.size());
         Assert.assertEquals("check consumers address", addressName1.toString(), array.getJsonObject(0).getString(ConsumerField.ADDRESS.getName()));
         Assert.assertEquals("check consumers address", addressName1.toString(), array.getJsonObject(1).getString(ConsumerField.ADDRESS.getName()));

         //test with empty filter - all consumers should be returned
         filterString = createJsonFilter("", "", "");
         consumersAsJsonString = serverControl.listConsumers(filterString, 1, 50);
         consumersAsJsonObject = JsonUtil.readJsonObject(consumersAsJsonString);
         array = (JsonArray) consumersAsJsonObject.get("data");

         Assert.assertTrue("at least 4 consumers returned from query", 4 <= array.size());

         //test with small page size
         consumersAsJsonString = serverControl.listConsumers(filterString, 1, 1);
         consumersAsJsonObject = JsonUtil.readJsonObject(consumersAsJsonString);
         array = (JsonArray) consumersAsJsonObject.get("data");

         Assert.assertEquals("number of consumers returned from query", 1, array.size());

         //test contents of returned consumer
         filterString = createJsonFilter(ConsumerField.QUEUE.getName(), "EQUALS", queueName3.toString());
         consumersAsJsonString = serverControl.listConsumers(filterString, 1, 50);
         consumersAsJsonObject = JsonUtil.readJsonObject(consumersAsJsonString);
         array = (JsonArray) consumersAsJsonObject.get("data");

         Assert.assertEquals("number of consumers returned from query", 1, array.size());
         JsonObject jsonConsumer = array.getJsonObject(0);
         Assert.assertEquals("queue name in consumer", queueName3.toString(), jsonConsumer.getString(ConsumerField.QUEUE.getName()));
         Assert.assertEquals("address name in consumer", addressName2.toString(), jsonConsumer.getString(ConsumerField.ADDRESS.getName()));
         Assert.assertEquals("consumer protocol ", "CORE", jsonConsumer.getString(ConsumerField.PROTOCOL.getName()));
         Assert.assertEquals("queue type", "anycast", jsonConsumer.getString(ConsumerField.QUEUE_TYPE.getName()));
         Assert.assertNotEquals("id", "", jsonConsumer.getString(ConsumerField.ID.getName()));
         Assert.assertNotEquals("session", "", jsonConsumer.getString(ConsumerField.SESSION.getName()));
         Assert.assertEquals("clientID", "", jsonConsumer.getString(ConsumerField.CLIENT_ID.getName()));
         Assert.assertEquals("user", "", jsonConsumer.getString(ConsumerField.USER.getName()));
         Assert.assertNotEquals("localAddress", "", jsonConsumer.getString(ConsumerField.LOCAL_ADDRESS.getName()));
         Assert.assertNotEquals("remoteAddress", "", jsonConsumer.getString(ConsumerField.REMOTE_ADDRESS.getName()));
         Assert.assertNotEquals("creationTime", "", jsonConsumer.getString(ConsumerField.CREATION_TIME.getName()));
         Assert.assertNotEquals("messagesInTransit", "", jsonConsumer.getString(ConsumerField.MESSAGES_IN_TRANSIT.getName()));
         Assert.assertNotEquals("messagesInTransitSize", "", jsonConsumer.getString(ConsumerField.MESSAGES_IN_TRANSIT_SIZE.getName()));
         Assert.assertNotEquals("messagesDelivered", "", jsonConsumer.getString(ConsumerField.MESSAGES_DELIVERED.getName()));
         Assert.assertNotEquals("messagesDeliveredSize", "", jsonConsumer.getString(ConsumerField.MESSAGES_DELIVERED_SIZE.getName()));
         Assert.assertNotEquals("messagesAcknowledged", "", jsonConsumer.getString(ConsumerField.MESSAGES_ACKNOWLEDGED.getName()));
         Assert.assertEquals("lastDeliveredTime", 0, jsonConsumer.getInt(ConsumerField.LAST_DELIVERED_TIME.getName()));
         Assert.assertEquals("lastAcknowledgedTime", 0, jsonConsumer.getInt(ConsumerField.LAST_ACKNOWLEDGED_TIME.getName()));
      }

   }

   @Test
   public void testListConsumersOrder() throws Exception {
      SimpleString queueName1 = new SimpleString("my_queue_one");
      SimpleString addressName1 = new SimpleString("my_address_one");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator)) {

         ClientSessionImpl session1 = (ClientSessionImpl) csf.createSession();
         ClientSessionImpl session2 = (ClientSessionImpl) csf.createSession();
         ClientSessionImpl session3 = (ClientSessionImpl) csf.createSession();

         //sleep - test compares creationTimes
         ClientConsumer consumer_s1 = session1.createConsumer(queueName1);
         Thread.sleep(500);
         ClientConsumer consumer_s2 = session2.createConsumer(queueName1);
         Thread.sleep(500);
         ClientConsumer consumer_s3 = session3.createConsumer(queueName1);

         //test default Order
         String filterString = createJsonFilter(ConsumerField.QUEUE.getName(), "EQUALS", queueName1.toString());
         String consumersAsJsonString = serverControl.listConsumers(filterString, 1, 50);
         JsonObject consumersAsJsonObject = JsonUtil.readJsonObject(consumersAsJsonString);
         JsonArray array = (JsonArray) consumersAsJsonObject.get("data");
         Assert.assertEquals("number of consumers returned from query", 3, array.size());

         Assert.assertEquals("Consumer1 default order", session1.getName(), array.getJsonObject(0).getString(ConsumerField.SESSION.getName()));
         Assert.assertEquals("Consumer2 default order", session2.getName(), array.getJsonObject(1).getString(ConsumerField.SESSION.getName()));
         Assert.assertEquals("Consumer3 default order", session3.getName(), array.getJsonObject(2).getString(ConsumerField.SESSION.getName()));

         //test ordered by creationTime
         filterString = createJsonFilter(ConsumerField.QUEUE.getName(), "EQUALS", queueName1.toString(), "creationTime", "desc");
         consumersAsJsonString = serverControl.listConsumers(filterString, 1, 50);
         consumersAsJsonObject = JsonUtil.readJsonObject(consumersAsJsonString);
         array = (JsonArray) consumersAsJsonObject.get("data");
         Assert.assertEquals("number of consumers returned from query", 3, array.size());

         Assert.assertEquals("Consumer3 creation time", session3.getName(), array.getJsonObject(0).getString(ConsumerField.SESSION.getName()));
         Assert.assertEquals("Consumer2 creation time", session2.getName(), array.getJsonObject(1).getString(ConsumerField.SESSION.getName()));
         Assert.assertEquals("Consumer1 creation time", session1.getName(), array.getJsonObject(2).getString(ConsumerField.SESSION.getName()));

      }
   }

   @Test
   public void testListSessions() throws Exception {
      SimpleString queueName1 = new SimpleString("my_queue_one");
      SimpleString addressName1 = new SimpleString("my_address_one");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator)) {

         ClientSessionImpl session1 = (ClientSessionImpl) csf.createSession();
         Thread.sleep(500);
         ClientSessionImpl session2 = (ClientSessionImpl) csf.createSession();
         Thread.sleep(500);
         ClientSessionImpl session3 = (ClientSessionImpl) csf.createSession();
         ClientConsumer consumer1_s1 = session1.createConsumer(queueName1);
         ClientConsumer consumer2_s1 = session1.createConsumer(queueName1);

         ClientConsumer consumer1_s2 = session2.createConsumer(queueName1);
         ClientConsumer consumer2_s2 = session2.createConsumer(queueName1);
         ClientConsumer consumer3_s2 = session2.createConsumer(queueName1);
         ClientConsumer consumer4_s2 = session2.createConsumer(queueName1);

         ClientConsumer consumer1_s3 = session3.createConsumer(queueName1);
         ClientConsumer consumer2_s3 = session3.createConsumer(queueName1);
         ClientConsumer consumer3_s3 = session3.createConsumer(queueName1);

         String filterString = createJsonFilter("CONSUMER_COUNT", "GREATER_THAN", "1");
         String sessionsAsJsonString = serverControl.listSessions(filterString, 1, 50);
         JsonObject sessionsAsJsonObject = JsonUtil.readJsonObject(sessionsAsJsonString);
         JsonArray array = (JsonArray) sessionsAsJsonObject.get("data");


         Assert.assertEquals("number of sessions returned from query", 3, array.size());
         JsonObject jsonSession = array.getJsonObject(0);

         //check all fields
         Assert.assertNotEquals("id", "", jsonSession.getString("id"));
         Assert.assertEquals("user", "", jsonSession.getString("user"));
         Assert.assertNotEquals("creationTime", "", jsonSession.getString("creationTime"));
         Assert.assertEquals("consumerCount", 2, jsonSession.getInt("consumerCount"));
         Assert.assertTrue("producerCount", 0 <= jsonSession.getInt("producerCount"));
         Assert.assertNotEquals("connectionID", "", jsonSession.getString("connectionID"));

         //check default order
         Assert.assertEquals("session1 location", session1.getName(), array.getJsonObject(0).getString("id"));
         Assert.assertEquals("session2 location", session2.getName(), array.getJsonObject(1).getString("id"));
         Assert.assertEquals("session3 location", session3.getName(), array.getJsonObject(2).getString("id"));

         //bring back session ordered by consumer count
         filterString = createJsonFilter("CONSUMER_COUNT", "GREATER_THAN", "1", "consumerCount", "asc");
         sessionsAsJsonString = serverControl.listSessions(filterString, 1, 50);
         sessionsAsJsonObject = JsonUtil.readJsonObject(sessionsAsJsonString);
         array = (JsonArray) sessionsAsJsonObject.get("data");

         Assert.assertTrue("number of sessions returned from query", 3 == array.size());
         Assert.assertEquals("session1 ordered by consumer", session1.getName(), array.getJsonObject(0).getString("id"));
         Assert.assertEquals("session3 ordered by consumer", session3.getName(), array.getJsonObject(1).getString("id"));
         Assert.assertEquals("session2 ordered by consumer", session2.getName(), array.getJsonObject(2).getString("id"));

         //bring back session ordered by consumer Count
         filterString = createJsonFilter("CONSUMER_COUNT", "GREATER_THAN", "1", "consumerCount", "asc");
         sessionsAsJsonString = serverControl.listSessions(filterString, 1, 50);
         sessionsAsJsonObject = JsonUtil.readJsonObject(sessionsAsJsonString);
         array = (JsonArray) sessionsAsJsonObject.get("data");

         Assert.assertTrue("number of sessions returned from query", 3 == array.size());
         Assert.assertEquals("session1 ordered by consumer", session1.getName(), array.getJsonObject(0).getString("id"));
         Assert.assertEquals("session3 ordered by consumer", session3.getName(), array.getJsonObject(1).getString("id"));
         Assert.assertEquals("session2 ordered by consumer", session2.getName(), array.getJsonObject(2).getString("id"));

         //bring back session ordered by creation time (desc)
         filterString = createJsonFilter("CONSUMER_COUNT", "GREATER_THAN", "1", "creationTime", "desc");
         sessionsAsJsonString = serverControl.listSessions(filterString, 1, 50);
         sessionsAsJsonObject = JsonUtil.readJsonObject(sessionsAsJsonString);
         array = (JsonArray) sessionsAsJsonObject.get("data");

         Assert.assertTrue("number of sessions returned from query", 3 == array.size());
         Assert.assertEquals("session3 ordered by creationTime", session3.getName(), array.getJsonObject(0).getString("id"));
         Assert.assertEquals("session2 ordered by creationTime", session2.getName(), array.getJsonObject(1).getString("id"));
         Assert.assertEquals("session1 ordered by creationTime", session1.getName(), array.getJsonObject(2).getString("id"));

      }
   }

   @RetryMethod(retries = 2) // the list of connections eventually comes from a hashmap on a different order. Which is fine but makes the test fail. a Retry is ok
   @Test
   public void testListConnections() throws Exception {
      SimpleString queueName1 = new SimpleString("my_queue_one");
      SimpleString addressName1 = new SimpleString("my_address_one");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientSessionFactoryImpl csf = null;
      ClientSessionFactoryImpl csf2 = null;
      ClientSessionFactoryImpl csf3 = null;

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator()) {

         //sleep as test compares creationTime
         csf = (ClientSessionFactoryImpl) createSessionFactory(locator);
         Thread.sleep(50);
         csf2 = (ClientSessionFactoryImpl) createSessionFactory(locator);
         Thread.sleep(50);
         csf3 = (ClientSessionFactoryImpl) createSessionFactory(locator);

         ClientSession session1_c1 = csf.createSession("guest", "guest", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());
         ClientSession session2_c1 = csf.createSession("guest", "guest", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());

         ClientSession session1_c2 = csf2.createSession("myUser", "myPass", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());
         ClientSession session2_c2 = csf2.createSession("myUser", "myPass", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());
         ClientSession session3_c2 = csf2.createSession("myUser", "myPass", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());
         ClientSession session4_c2 = csf2.createSession("myUser", "myPass", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());

         ClientSession session1_c4 = csf3.createSession("myUser", "myPass", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());
         ClientSession session2_c4 = csf3.createSession("guest", "guest", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());
         ClientSession session3_c4 = csf3.createSession("guest", "guest", false, true, true, locator.isPreAcknowledge(), locator.getAckBatchSize());

         String filterString = createJsonFilter("SESSION_COUNT", "GREATER_THAN", "1");
         String connectionsAsJsonString = serverControl.listConnections(filterString, 1, 50);
         System.err.println(connectionsAsJsonString);
         JsonObject connectionsAsJsonObject = JsonUtil.readJsonObject(connectionsAsJsonString);
         JsonArray array = (JsonArray) connectionsAsJsonObject.get("data");

         Assert.assertEquals("number of connections returned from query", 3, array.size());
         JsonObject jsonConnection = array.getJsonObject(0);

         //check all fields
         Assert.assertNotEquals("connectionID", "", jsonConnection.getString("connectionID"));
         Assert.assertNotEquals("remoteAddress", "", jsonConnection.getString("remoteAddress"));
         Assert.assertEquals("users", "guest", jsonConnection.getString("users"));
         Assert.assertNotEquals("creationTime", "", jsonConnection.getString("creationTime"));
         Assert.assertNotEquals("implementation", "", jsonConnection.getString("implementation"));
         Assert.assertNotEquals("protocol", "", jsonConnection.getString("protocol"));
         Assert.assertEquals("clientID", "", jsonConnection.getString("clientID"));
         Assert.assertNotEquals("localAddress", "", jsonConnection.getString("localAddress"));
         Assert.assertEquals("sessionCount", 2, jsonConnection.getInt("sessionCount"));

         //check default order
         Assert.assertEquals("connection1 default Order", csf.getConnection().getID(), array.getJsonObject(0).getString("connectionID"));
         Assert.assertEquals("connection2 default Order", csf2.getConnection().getID(), array.getJsonObject(1).getString("connectionID"));
         Assert.assertEquals("connection3 session Order", csf3.getConnection().getID(), array.getJsonObject(2).getString("connectionID"));

         //check order by users asc
         filterString = createJsonFilter("SESSION_COUNT", "GREATER_THAN", "1", "users", "asc");
         connectionsAsJsonString = serverControl.listConnections(filterString, 1, 50);
         connectionsAsJsonObject = JsonUtil.readJsonObject(connectionsAsJsonString);
         array = (JsonArray) connectionsAsJsonObject.get("data");

         Assert.assertEquals("number of connections returned from query", 3, array.size());
         Assert.assertEquals("connection1 users Order", "guest", array.getJsonObject(0).getString("users"));
         Assert.assertEquals("connection3 users Order", "guest,myUser", array.getJsonObject(1).getString("users"));
         Assert.assertEquals("connection2 users Order", "myUser", array.getJsonObject(2).getString("users"));

         //check order by users desc
         filterString = createJsonFilter("SESSION_COUNT", "GREATER_THAN", "1", "users", "desc");
         connectionsAsJsonString = serverControl.listConnections(filterString, 1, 50);
         connectionsAsJsonObject = JsonUtil.readJsonObject(connectionsAsJsonString);
         array = (JsonArray) connectionsAsJsonObject.get("data");

         Assert.assertEquals("number of connections returned from query", 3, array.size());
         Assert.assertEquals("connection2 users Order", "myUser", array.getJsonObject(0).getString("users"));
         Assert.assertEquals("connection3 users Order", "guest,myUser", array.getJsonObject(1).getString("users"));
         Assert.assertEquals("connection1 users Order", "guest", array.getJsonObject(2).getString("users"));

         //check order by session count desc
         filterString = createJsonFilter("SESSION_COUNT", "GREATER_THAN", "1", "sessionCount", "desc");
         connectionsAsJsonString = serverControl.listConnections(filterString, 1, 50);
         connectionsAsJsonObject = JsonUtil.readJsonObject(connectionsAsJsonString);
         array = (JsonArray) connectionsAsJsonObject.get("data");

         Assert.assertEquals("number of connections returned from query", 3, array.size());
         Assert.assertEquals("connection2 session Order", csf2.getConnection().getID(), array.getJsonObject(0).getString("connectionID"));
         Assert.assertEquals("connection3 session Order", csf3.getConnection().getID(), array.getJsonObject(1).getString("connectionID"));
         Assert.assertEquals("connection1 session Order", csf.getConnection().getID(), array.getJsonObject(2).getString("connectionID"));

         //check order by creationTime desc
         filterString = createJsonFilter("SESSION_COUNT", "GREATER_THAN", "1", "creationTime", "desc");
         connectionsAsJsonString = serverControl.listConnections(filterString, 1, 50);
         connectionsAsJsonObject = JsonUtil.readJsonObject(connectionsAsJsonString);
         array = (JsonArray) connectionsAsJsonObject.get("data");

         Assert.assertEquals("number of connections returned from query", 3, array.size());
         Assert.assertEquals("connection3 creationTime Order", csf3.getConnection().getID(), array.getJsonObject(0).getString("connectionID"));
         Assert.assertEquals("connection2 creationTime Order", csf2.getConnection().getID(), array.getJsonObject(1).getString("connectionID"));
         Assert.assertEquals("connection1 creationTime Order", csf.getConnection().getID(), array.getJsonObject(2).getString("connectionID"));
      } finally {
         if (csf != null) {
            csf.close();
         }
         if (csf2 != null) {
            csf.close();
         }
         if (csf3 != null) {
            csf.close();
         }
      }
   }

   @Test
   public void testListConnectionsClientID() throws Exception {
      SimpleString queueName1 = new SimpleString("my_queue_one");
      SimpleString addressName1 = new SimpleString("my_address_one");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      ClientSessionFactoryImpl csf = null;

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator()) {
         //sleep as test compares creationTime
         csf = (ClientSessionFactoryImpl) createSessionFactory(locator);
         ClientSession session1_c1 = csf.createSession();
         ClientSession session2_c1 = csf.createSession();
         session1_c1.addMetaData(ClientSession.JMS_SESSION_IDENTIFIER_PROPERTY, "");
         session1_c1.addMetaData(ClientSession.JMS_SESSION_CLIENT_ID_PROPERTY, "MYClientID");

         String filterString = createJsonFilter("SESSION_COUNT", "GREATER_THAN", "1");
         String connectionsAsJsonString = serverControl.listConnections(filterString, 1, 50);
         JsonObject connectionsAsJsonObject = JsonUtil.readJsonObject(connectionsAsJsonString);
         JsonArray array = (JsonArray) connectionsAsJsonObject.get("data");

         Assert.assertEquals("number of connections returned from query", 1, array.size());
         JsonObject jsonConnection = array.getJsonObject(0);

         //check all fields
         Assert.assertEquals("clientID", "MYClientID", jsonConnection.getString("clientID"));
      } finally {
         if (csf != null) {
            csf.close();
         }
      }
   }

   @Test
   public void testListProducers() throws Exception {
      SimpleString queueName1 = new SimpleString("my_queue_one");
      SimpleString addressName1 = new SimpleString("my_address_one");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator)) {

         ClientSession session1 = csf.createSession();
         ClientSession session2 = csf.createSession();

         ClientProducer producer1 = session1.createProducer(addressName1);
         producer1.send(session1.createMessage(true));
         ClientProducer producer2 = session2.createProducer(addressName1);
         producer2.send(session2.createMessage(true));

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         Assert.assertEquals("number of producers returned from query", 2 + extraProducers, array.size());

         boolean foundElement = false;
         for (int i = 0; i < array.size(); i++) {

            JsonObject jsonSession = array.getJsonObject(i);
            if (jsonSession.getString("address").equals("activemq.management")) {
               continue;
            }

            foundElement = true;

            //check all fields
            Assert.assertNotEquals(ProducerField.ID.getName(), "", jsonSession.getString(ProducerField.ID.getName()));
            Assert.assertNotEquals(ProducerField.SESSION.getName(), "", jsonSession.getString(ProducerField.SESSION.getName()));
            Assert.assertEquals(ProducerField.CLIENT_ID.getName(), "", jsonSession.getString(ProducerField.CLIENT_ID.getName()));
            Assert.assertEquals(ProducerField.USER.getName(), "", jsonSession.getString(ProducerField.USER.getName()));
            Assert.assertNotEquals(ProducerField.PROTOCOL.getName(), "", jsonSession.getString(ProducerField.PROTOCOL.getName()));
            Assert.assertEquals(ProducerField.ADDRESS.getName(), addressName1.toString(), jsonSession.getString(ProducerField.ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.LOCAL_ADDRESS.getName(), "", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.REMOTE_ADDRESS.getName(), "", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.CREATION_TIME.getName(), "", jsonSession.getString(ProducerField.CREATION_TIME.getName()));
         }


         Assert.assertTrue("Valid session not found", foundElement);
      }
   }

   @Test
   public void testListProducersLargeMessages() throws Exception {
      SimpleString queueName1 = new SimpleString("my_queue_one");
      SimpleString addressName1 = new SimpleString("my_address_one");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator)) {

         ClientSession session1 = csf.createSession();
         ClientSession session2 = csf.createSession();

         ClientProducer producer1 = session1.createProducer(addressName1);
         producer1.send(session1.createMessage(true));
         ClientProducer producer2 = session2.createProducer(addressName1);
         producer2.send(session2.createMessage(true));

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         Assert.assertEquals("number of producers returned from query", 2 + extraProducers, array.size());

         boolean foundElement = false;
         for (int i = 0; i < array.size(); i++) {

            JsonObject jsonSession = array.getJsonObject(i);
            if (jsonSession.getString("address").equals("activemq.management")) {
               continue;
            }

            foundElement = true;

            //check all fields
            Assert.assertNotEquals(ProducerField.ID.getName(), "", jsonSession.getString(ProducerField.ID.getName()));
            Assert.assertNotEquals(ProducerField.SESSION.getName(), "", jsonSession.getString(ProducerField.SESSION.getName()));
            Assert.assertEquals(ProducerField.CLIENT_ID.getName(), "", jsonSession.getString(ProducerField.CLIENT_ID.getName()));
            Assert.assertEquals(ProducerField.USER.getName(), "", jsonSession.getString(ProducerField.USER.getName()));
            Assert.assertNotEquals(ProducerField.PROTOCOL.getName(), "", jsonSession.getString(ProducerField.PROTOCOL.getName()));
            Assert.assertEquals(ProducerField.ADDRESS.getName(), addressName1.toString(), jsonSession.getString(ProducerField.ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.LOCAL_ADDRESS.getName(), "", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.REMOTE_ADDRESS.getName(), "", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.CREATION_TIME.getName(), "", jsonSession.getString(ProducerField.CREATION_TIME.getName()));
         }


         Assert.assertTrue("Valid session not found", foundElement);
      }
   }

   @Test
   public void testListProducersAsJSONAgainstServer() throws Exception {
      SimpleString queueName1 = new SimpleString("my_queue_one");
      SimpleString addressName1 = new SimpleString("my_address_one");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator)) {

         ClientSession session1 = csf.createSession();

         ClientProducer producer1 = session1.createProducer(addressName1);
         producer1.send(session1.createMessage(true));

         //bring back all producers
         String producersAsJsonString = serverControl.listProducersInfoAsJSON();
         JsonArray array = JsonUtil.readJsonArray(producersAsJsonString);

         Assert.assertEquals("number of producers returned from query", 1 + extraProducers, array.size());

         JsonObject jsonSession = array.getJsonObject(0);
         if (!jsonSession.getString(ProducerField.ADDRESS.getAlternativeName()).equalsIgnoreCase(addressName1.toString())) {
            jsonSession = array.getJsonObject(1);
         }

         //get the only server producer
         ServerProducer producer = server.getSessionByID(jsonSession.getString(ProducerField.SESSION.getAlternativeName())).getServerProducers().iterator().next();
         //check all fields
         Assert.assertEquals(ProducerField.ID.getName(), String.valueOf(producer.getID()), jsonSession.getString(ProducerField.ID.getName()));
         Assert.assertEquals(ProducerField.NAME.getName(), producer.getName(), jsonSession.getString(ProducerField.NAME.getName()));
         Assert.assertEquals(ProducerField.SESSION.getName(), producer.getSessionID(), jsonSession.getString(ProducerField.SESSION.getAlternativeName()));
         Assert.assertEquals(ProducerField.ADDRESS.getName(), producer.getAddress(), jsonSession.getString(ProducerField.ADDRESS.getAlternativeName()));
         Assert.assertEquals(ProducerField.MESSAGE_SENT.getName(), producer.getMessagesSent(), jsonSession.getJsonNumber(ProducerField.MESSAGE_SENT.getName()).longValue());
         Assert.assertEquals(ProducerField.MESSAGE_SENT_SIZE.getName(), producer.getMessagesSentSize(), jsonSession.getJsonNumber(ProducerField.MESSAGE_SENT_SIZE.getName()).longValue());
         Assert.assertEquals(ProducerField.CREATION_TIME.getName(), String.valueOf(producer.getCreationTime()), jsonSession.getString(ProducerField.CREATION_TIME.getName()));
      }
   }

   private int getNumberOfProducers(ActiveMQServer server) {
      AtomicInteger producers = new AtomicInteger();
      server.getSessions().forEach(session -> {
         producers.addAndGet(session.getProducerCount());
      });
      return producers.get();
   }

   @Test
   public void testListProducersAgainstServer() throws Exception {
      SimpleString queueName1 = new SimpleString("my_queue_one");
      SimpleString addressName1 = new SimpleString("my_address_one");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator)) {

         ClientSession session1 = csf.createSession();

         ClientProducer producer1 = session1.createProducer(addressName1);
         producer1.send(session1.createMessage(true));

         Wait.assertEquals(1, () -> getNumberOfProducers(server));

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         Assert.assertEquals("number of producers returned from query", 1 + extraProducers, array.size());

         JsonObject jsonSession = array.getJsonObject(0);

         Wait.assertTrue(() -> server.getSessionByID(jsonSession.getString(ProducerField.SESSION.getName())) != null);
         //get the only server producer
         ServerProducer producer = server.getSessionByID(jsonSession.getString(ProducerField.SESSION.getName())).getServerProducers().iterator().next();
         //check all fields
         Assert.assertEquals(ProducerField.ID.getName(), String.valueOf(producer.getID()), jsonSession.getString(ProducerField.ID.getName()));
         Assert.assertEquals(ProducerField.SESSION.getName(), producer.getSessionID(), jsonSession.getString(ProducerField.SESSION.getName()));
         Assert.assertEquals(ProducerField.PROTOCOL.getName(), producer.getProtocol(), jsonSession.getString(ProducerField.PROTOCOL.getName()));
         Assert.assertEquals(ProducerField.ADDRESS.getName(), producer.getAddress(), jsonSession.getString(ProducerField.ADDRESS.getName()));
         Assert.assertEquals(ProducerField.MESSAGE_SENT.getName(), producer.getMessagesSent(), jsonSession.getJsonNumber(ProducerField.MESSAGE_SENT.getName()).longValue());
         Assert.assertEquals(ProducerField.MESSAGE_SENT_SIZE.getName(), producer.getMessagesSentSize(), jsonSession.getJsonNumber(ProducerField.MESSAGE_SENT_SIZE.getName()).longValue());
         Assert.assertEquals(ProducerField.CREATION_TIME.getName(), String.valueOf(producer.getCreationTime()), jsonSession.getString(ProducerField.CREATION_TIME.getName()));
      }
   }
   @Test
   public void testListProducersJMSCore() throws Exception {
      testListProducersJMS(ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY)), "CORE");
   }

   @Test
   public void testListProducersJMSAMQP() throws Exception {
      testListProducersJMS(new JmsConnectionFactory("amqp://localhost:61616"), "AMQP");
   }

   @Test
   public void testListProducersJMSOW() throws Exception {
      testListProducersJMS(new org.apache.activemq.ActiveMQConnectionFactory("tcp://localhost:61616"), "CORE");
   }

   public void testListProducersJMS(ConnectionFactory connectionFactory, String protocol) throws Exception {
      String queueName = "my_queue_one";
      SimpleString ssQueueName = SimpleString.toSimpleString(queueName);
      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(ssQueueName, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(ssQueueName, RoutingType.ANYCAST, ssQueueName, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName).setAddress(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }
      // create some producers
      try (Connection conn = connectionFactory.createConnection()) {

         Session session1 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session session2 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer producer1 = session1.createProducer(session1.createQueue(queueName));
         producer1.send(session1.createMessage());
         MessageProducer producer2 = session2.createProducer(session2.createQueue(queueName));
         producer2.send(session2.createMessage());

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         Assert.assertEquals("number of producers returned from query", 2 + extraProducers, array.size());

         boolean foundElement = false;
         for (int i = 0; i < array.size(); i++) {

            JsonObject jsonSession = array.getJsonObject(i);
            if (jsonSession.getString("address").equals("activemq.management")) {
               continue;
            }

            foundElement = true;

            //check all fields
            Assert.assertNotEquals(ProducerField.ID.getName(), "", jsonSession.getString(ProducerField.ID.getName()));
            Assert.assertNotEquals(ProducerField.SESSION.getName(), "", jsonSession.getString(ProducerField.SESSION.getName()));
            Assert.assertEquals(ProducerField.USER.getName(), "", jsonSession.getString(ProducerField.USER.getName()));
            Assert.assertNotEquals(ProducerField.PROTOCOL.getName(), "", jsonSession.getString(ProducerField.PROTOCOL.getName()));
            Assert.assertEquals(ProducerField.ADDRESS.getName(), queueName, jsonSession.getString(ProducerField.ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.LOCAL_ADDRESS.getName(), "", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.REMOTE_ADDRESS.getName(), "", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.CREATION_TIME.getName(), "", jsonSession.getString(ProducerField.CREATION_TIME.getName()));
         }

         Assert.assertTrue("Valid session not found", foundElement);
      }
   }

   @Test
   public void testListProducersJMSLegacy() throws Exception {
      String queueName = "my_queue_one";
      SimpleString ssQueueName = SimpleString.toSimpleString(queueName);
      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(ssQueueName, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(ssQueueName, RoutingType.ANYCAST, ssQueueName, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName).setAddress(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      //pretend we are an olde client by removing the producer id and changing the session to use lagacy producers
      server.getBrokerSessionPlugins().add(new ActiveMQServerSessionPlugin() {
         @Override
         public void beforeCreateSession(String name, String username, int minLargeMessageSize, RemotingConnection connection, boolean autoCommitSends, boolean autoCommitAcks, boolean preAcknowledge, boolean xa, String defaultAddress, SessionCallback callback, boolean autoCreateQueues, OperationContext context, Map<SimpleString, RoutingType> prefixes) throws ActiveMQException {
            ActiveMQServerSessionPlugin.super.beforeCreateSession(name, username, minLargeMessageSize, connection, autoCommitSends, autoCommitAcks, preAcknowledge, xa, defaultAddress, callback, autoCreateQueues, context, prefixes);
         }

         @Override
         public void afterCreateSession(ServerSession session) throws ActiveMQException {
            try {
               Field serverProducers = session.getClass().getDeclaredField("serverProducers");
               serverProducers.setAccessible(true);
               serverProducers.set(session, new ServerLegacyProducersImpl(session));
            } catch (NoSuchFieldException e) {
               throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
               throw new RuntimeException(e);
            }
         }
      });

      ActiveMQConnectionFactory connectionFactory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));
      // create some producers
      try (Connection conn = connectionFactory.createConnection()) {

         Session session1 = conn.createSession();
         Session session2 = conn.createSession();

         MessageProducer producer1 = session1.createProducer(session1.createQueue(queueName));
         producer1.send(session1.createMessage());
         MessageProducer producer2 = session2.createProducer(session2.createQueue(queueName));
         producer2.send(session2.createMessage());

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         Assert.assertEquals("number of producers returned from query", 2, array.size());

         boolean foundElement = false;
         for (int i = 0; i < array.size(); i++) {

            JsonObject jsonSession = array.getJsonObject(i);
            if (jsonSession.getString("address").equals("activemq.management")) {
               continue;
            }

            foundElement = true;

            //check all fields
            Assert.assertNotEquals(ProducerField.ID.getName(), "", jsonSession.getString(ProducerField.ID.getName()));
            Assert.assertNotEquals(ProducerField.SESSION.getName(), "", jsonSession.getString(ProducerField.SESSION.getName()));
            Assert.assertEquals(ProducerField.USER.getName(), "", jsonSession.getString(ProducerField.USER.getName()));
            Assert.assertNotEquals(ProducerField.PROTOCOL.getName(), "", jsonSession.getString(ProducerField.PROTOCOL.getName()));
            Assert.assertEquals(ProducerField.ADDRESS.getName(), queueName, jsonSession.getString(ProducerField.ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.LOCAL_ADDRESS.getName(), "", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.REMOTE_ADDRESS.getName(), "", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.CREATION_TIME.getName(), "", jsonSession.getString(ProducerField.CREATION_TIME.getName()));
         }

         Assert.assertTrue("Valid session not found", foundElement);

         session1.close();

         filterString = createJsonFilter("", "", "");
         producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         array = (JsonArray) producersAsJsonObject.get("data");

         Assert.assertEquals("number of producers returned from query", 1, array.size());

         session2.close();

         filterString = createJsonFilter("", "", "");
         producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         array = (JsonArray) producersAsJsonObject.get("data");

         Assert.assertEquals("number of producers returned from query", 0, array.size());
      }
   }

   @Test
   public void testListFqqnProducersJMSLegacy() throws Exception {
      SimpleString queueName1 = new SimpleString("my_queue_one");
      SimpleString addressName1 = new SimpleString("my_address_one");
      SimpleString FQQNName = new SimpleString(addressName1 + "::" + queueName1);
      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(FQQNName, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(FQQNName, RoutingType.ANYCAST, FQQNName, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(FQQNName).setAddress(FQQNName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      //pretend we are an olde client by removing the producer id and changing the session to use lagacy producers
      server.getBrokerSessionPlugins().add(new ActiveMQServerSessionPlugin() {
         @Override
         public void beforeCreateSession(String name, String username, int minLargeMessageSize, RemotingConnection connection, boolean autoCommitSends, boolean autoCommitAcks, boolean preAcknowledge, boolean xa, String defaultAddress, SessionCallback callback, boolean autoCreateQueues, OperationContext context, Map<SimpleString, RoutingType> prefixes) throws ActiveMQException {
            ActiveMQServerSessionPlugin.super.beforeCreateSession(name, username, minLargeMessageSize, connection, autoCommitSends, autoCommitAcks, preAcknowledge, xa, defaultAddress, callback, autoCreateQueues, context, prefixes);
         }

         @Override
         public void afterCreateSession(ServerSession session) throws ActiveMQException {
            try {
               Field serverProducers = session.getClass().getDeclaredField("serverProducers");
               serverProducers.setAccessible(true);
               serverProducers.set(session, new ServerLegacyProducersImpl(session));
            } catch (NoSuchFieldException e) {
               throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
               throw new RuntimeException(e);
            }
         }
      });
      ActiveMQConnectionFactory connectionFactory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));
      // create some producers
      try (Connection conn = connectionFactory.createConnection()) {

         Session session1 = conn.createSession();

         MessageProducer producer1 = session1.createProducer(session1.createQueue(FQQNName.toString()));
         producer1.send(session1.createMessage());

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 200);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         //this is testing that the producers tracked hasnt exceededd the max of 100
         Assert.assertEquals("number of producers returned from query", 1, array.size());

         boolean foundElement = false;
         for (int i = 0; i < array.size(); i++) {

            JsonObject jsonSession = array.getJsonObject(i);
            if (jsonSession.getString("address").equals("activemq.management")) {
               continue;
            }

            foundElement = true;

            //check all fields
            Assert.assertNotEquals(ProducerField.ID.getName(), "", jsonSession.getString(ProducerField.ID.getName()));
            Assert.assertNotEquals(ProducerField.SESSION.getName(), "", jsonSession.getString(ProducerField.SESSION.getName()));
            Assert.assertEquals(ProducerField.USER.getName(), "", jsonSession.getString(ProducerField.USER.getName()));
            Assert.assertNotEquals(ProducerField.PROTOCOL.getName(), "", jsonSession.getString(ProducerField.PROTOCOL.getName()));
            Assert.assertEquals(ProducerField.ADDRESS.getName(), FQQNName.toString(), jsonSession.getString(ProducerField.ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.LOCAL_ADDRESS.getName(), "", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.REMOTE_ADDRESS.getName(), "", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.CREATION_TIME.getName(), "", jsonSession.getString(ProducerField.CREATION_TIME.getName()));
         }

         Assert.assertTrue("Valid session not found", foundElement);
      }
   }

   @Test
   public void testListAnonProducersJMSLegacy() throws Exception {
      String queueName = "my_queue_one";
      SimpleString ssQueueName = SimpleString.toSimpleString(queueName);
      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(ssQueueName, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(ssQueueName, RoutingType.ANYCAST, ssQueueName, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName).setAddress(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      //pretend we are an olde client by removing the producer id and changing the session to use lagacy producers
      server.getBrokerSessionPlugins().add(new ActiveMQServerSessionPlugin() {
         @Override
         public void beforeCreateSession(String name, String username, int minLargeMessageSize, RemotingConnection connection, boolean autoCommitSends, boolean autoCommitAcks, boolean preAcknowledge, boolean xa, String defaultAddress, SessionCallback callback, boolean autoCreateQueues, OperationContext context, Map<SimpleString, RoutingType> prefixes) throws ActiveMQException {
            ActiveMQServerSessionPlugin.super.beforeCreateSession(name, username, minLargeMessageSize, connection, autoCommitSends, autoCommitAcks, preAcknowledge, xa, defaultAddress, callback, autoCreateQueues, context, prefixes);
         }

         @Override
         public void afterCreateSession(ServerSession session) throws ActiveMQException {
            try {
               Field serverProducers = session.getClass().getDeclaredField("serverProducers");
               serverProducers.setAccessible(true);
               serverProducers.set(session, new ServerLegacyProducersImpl(session));
            } catch (NoSuchFieldException e) {
               throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
               throw new RuntimeException(e);
            }
         }
      });

      ActiveMQConnectionFactory connectionFactory = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));
      // create some producers
      try (Connection conn = connectionFactory.createConnection()) {

         Session session1 = conn.createSession();

         MessageProducer producer1 = session1.createProducer(null);
         for ( int i = 0; i < 110; i++) {
            javax.jms.Queue queue = session1.createQueue(queueName + i);
            producer1.send(queue, session1.createMessage());
         }

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 200);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         //this is testing that the producers tracked hasnt exceededd the max of 100
         Assert.assertEquals("number of producers returned from query", 100, array.size());

         boolean foundElement = false;
         for (int i = 0; i < array.size(); i++) {

            JsonObject jsonSession = array.getJsonObject(i);
            if (jsonSession.getString("address").equals("activemq.management")) {
               continue;
            }

            foundElement = true;

            //check all fields
            Assert.assertNotEquals(ProducerField.ID.getName(), "", jsonSession.getString(ProducerField.ID.getName()));
            Assert.assertNotEquals(ProducerField.SESSION.getName(), "", jsonSession.getString(ProducerField.SESSION.getName()));
            Assert.assertEquals(ProducerField.USER.getName(), "", jsonSession.getString(ProducerField.USER.getName()));
            Assert.assertNotEquals(ProducerField.PROTOCOL.getName(), "", jsonSession.getString(ProducerField.PROTOCOL.getName()));
            Assert.assertNotEquals(ProducerField.LOCAL_ADDRESS.getName(), "", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.REMOTE_ADDRESS.getName(), "", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.CREATION_TIME.getName(), "", jsonSession.getString(ProducerField.CREATION_TIME.getName()));
         }

         Assert.assertTrue("Valid session not found", foundElement);

         session1.close();

         filterString = createJsonFilter("", "", "");
         producersAsJsonString = serverControl.listProducers(filterString, 1, 110);
         producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         array = (JsonArray) producersAsJsonObject.get("data");

         Assert.assertEquals("number of producers returned from query", 0, array.size());
      }
   }

   @Test
   public void testListProducersJMSCoreMultipleProducers() throws Exception {
      testListProducersJMSMultipleProducers(ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY)), "CORE");
   }

   @Test
   public void testListProducersJMSAMQPMultipleProducers() throws Exception {
      testListProducersJMSMultipleProducers(new JmsConnectionFactory("amqp://localhost:61616"), "AMQP");
   }

   @Test
   public void testListProducersJMSOpenWireMultipleProducers() throws Exception {
      testListProducersJMSMultipleProducers(new org.apache.activemq.ActiveMQConnectionFactory("tcp://localhost:61616"), "OPENWIRE");
   }
   public void testListProducersJMSMultipleProducers(ConnectionFactory connectionFactory, String protocol) throws Exception {
      String queueName = "my_queue_one";
      SimpleString ssQueueName = SimpleString.toSimpleString(queueName);
      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(ssQueueName, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(ssQueueName, RoutingType.ANYCAST, ssQueueName, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName).setAddress(queueName).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }
      // create some producers
      try (Connection conn = connectionFactory.createConnection()) {

         Session session1 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         MessageProducer producer1 = session1.createProducer(session1.createQueue(queueName));
         producer1.send(session1.createMessage());

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         Assert.assertEquals("number of producers returned from query", 1 + extraProducers, array.size());

         boolean foundElement = false;
         for (int i = 0; i < array.size(); i++) {

            JsonObject jsonSession = array.getJsonObject(i);
            if (jsonSession.getString("address").equals("activemq.management")) {
               continue;
            }

            foundElement = true;

            //check all fields
            Assert.assertNotEquals(ProducerField.ID.getName(), "", jsonSession.getString(ProducerField.ID.getName()));
            Assert.assertNotEquals(ProducerField.SESSION.getName(), "", jsonSession.getString(ProducerField.SESSION.getName()));
            Assert.assertEquals(ProducerField.USER.getName(), "", jsonSession.getString(ProducerField.USER.getName()));
            Assert.assertNotEquals(ProducerField.PROTOCOL.getName(), "", jsonSession.getString(ProducerField.PROTOCOL.getName()));
            Assert.assertEquals(ProducerField.ADDRESS.getName(), queueName, jsonSession.getString(ProducerField.ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.LOCAL_ADDRESS.getName(), "", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.REMOTE_ADDRESS.getName(), "", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.CREATION_TIME.getName(), "", jsonSession.getString(ProducerField.CREATION_TIME.getName()));
         }

         MessageProducer producer2 = session1.createProducer(session1.createQueue(queueName));
         producer2.send(session1.createMessage());

         producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         array = (JsonArray) producersAsJsonObject.get("data");

         Assert.assertEquals("number of producers returned from query", 2 + extraProducers, array.size());

         producer1.close();

         Wait.assertEquals(1 + extraProducers, () -> ((JsonArray) JsonUtil.readJsonObject(serverControl.listProducers(filterString, 1, 50)).get("data")).size());

         producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         array = (JsonArray) producersAsJsonObject.get("data");

         Assert.assertEquals("number of producers returned from query", 1 + extraProducers, array.size());

         Assert.assertTrue("Valid session not found", foundElement);
      }
   }

   @Test
   public void testListProducersJMSAnonCore() throws Exception {
      testListProducersJMSAnon(ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY)), "CORE");
   }

   @Test
   public void testListProducersJMSAnonAMQP() throws Exception {
      testListProducersJMSAnon(new JmsConnectionFactory("amqp://localhost:61616"), "AMQP");
   }

   @Test
   public void testListProducersJMSAnonOW() throws Exception {
      testListProducersJMSAnon(new org.apache.activemq.ActiveMQConnectionFactory("tcp://localhost:61616"), "OPENWIRE");
   }

   public void testListProducersJMSAnon(ConnectionFactory connectionFactory, String protocol) throws Exception {
      String queueName1 = "my_queue_one";
      String queueName2 = "my_queue_two";
      String queueName3 = "my_queue_three";
      SimpleString ssQueueName1 = SimpleString.toSimpleString(queueName1);
      SimpleString ssQueueName2 = SimpleString.toSimpleString(queueName2);
      SimpleString ssQueueName3 = SimpleString.toSimpleString(queueName3);
      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(ssQueueName1, RoutingType.ANYCAST));
      server.addAddressInfo(new AddressInfo(ssQueueName2, RoutingType.ANYCAST));
      server.addAddressInfo(new AddressInfo(ssQueueName3, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(ssQueueName1, RoutingType.ANYCAST, ssQueueName1, null, false, false);
         server.createQueue(ssQueueName2, RoutingType.ANYCAST, ssQueueName2, null, false, false);
         server.createQueue(ssQueueName3, RoutingType.ANYCAST, ssQueueName3, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName1).setAddress(queueName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         server.createQueue(new QueueConfiguration(queueName2).setAddress(queueName2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         server.createQueue(new QueueConfiguration(queueName3).setAddress(queueName3).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }
      // create some producers
      try (Connection conn = connectionFactory.createConnection()) {

         Session session1 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session session2 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue queue1 = session1.createQueue(queueName1);
         javax.jms.Queue queue2 = session1.createQueue(queueName2);
         javax.jms.Queue queue3 = session1.createQueue(queueName3);
         MessageProducer producer1 = session1.createProducer(null);
         producer1.send(queue1, session1.createMessage());
         producer1.send(queue2, session1.createMessage());
         producer1.send(queue3, session1.createMessage());
         MessageProducer producer2 = session2.createProducer(null);
         producer2.send(queue1, session2.createMessage());
         producer2.send(queue2, session2.createMessage());
         producer2.send(queue3, session2.createMessage());

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         Assert.assertEquals("number of producers returned from query", 2 + extraProducers, array.size());

         boolean foundElement = false;
         for (int i = 0; i < array.size(); i++) {

            JsonObject jsonSession = array.getJsonObject(i);
            if (jsonSession.getString("address").equals("activemq.management")) {
               continue;
            }

            foundElement = true;

            //check all fields
            Assert.assertNotEquals(ProducerField.ID.getName(), "", jsonSession.getString(ProducerField.ID.getName()));
            Assert.assertNotEquals(ProducerField.SESSION.getName(), "", jsonSession.getString(ProducerField.SESSION.getName()));
            Assert.assertEquals(ProducerField.USER.getName(), "", jsonSession.getString(ProducerField.USER.getName()));
            Assert.assertNotEquals(ProducerField.PROTOCOL.getName(), "", jsonSession.getString(ProducerField.PROTOCOL.getName()));
            Assert.assertNotEquals(ProducerField.LOCAL_ADDRESS.getName(), "", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.REMOTE_ADDRESS.getName(), "", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.CREATION_TIME.getName(), "", jsonSession.getString(ProducerField.CREATION_TIME.getName()));
         }

         Assert.assertTrue("Valid session not found", foundElement);
      }
   }

   @Test
   public void testListProducersJMSTopicCore() throws Exception {
      testListProducersJMSTopic(ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY)), "CORE");
   }

   @Test
   public void testListProducersJMSTopicAMQP() throws Exception {
      testListProducersJMSTopic(new JmsConnectionFactory("amqp://localhost:61616"), "AMQP");
   }

   @Test
   public void testListProducersJMSTopicOW() throws Exception {
      testListProducersJMSTopic(new org.apache.activemq.ActiveMQConnectionFactory("tcp://localhost:61616"), "OPENWIRE");
   }
   public void testListProducersJMSTopic(ConnectionFactory connectionFactory, String protocol) throws Exception {
      SimpleString topicName = SimpleString.toSimpleString("my-topic");
      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(topicName, RoutingType.MULTICAST));
      if (legacyCreateQueue) {
         return;
      }

      server.addAddressInfo(new AddressInfo(""));

      try (Connection conn = connectionFactory.createConnection()) {
         conn.setClientID("clientID");
         Session session1 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
         Session session2 = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);

         Topic topic = session1.createTopic(topicName.toString());
         TopicSubscriber mysub1 = session1.createDurableSubscriber(topic, "mysub1");
         TopicSubscriber mysub2 = session2.createDurableSubscriber(topic, "mysub2");
         MessageProducer producer1 = session1.createProducer(topic);
         producer1.send(session1.createMessage());
         MessageProducer producer2 = session2.createProducer(session2.createTopic(topicName.toString()));
         producer2.send(session2.createMessage());

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         Assert.assertEquals("number of producers returned from query", 2 + extraProducers, array.size());

         boolean foundElement = false;
         for (int i = 0; i < array.size(); i++) {

            JsonObject jsonSession = array.getJsonObject(i);
            if (jsonSession.getString("address").equals("activemq.management")) {
               continue;
            }

            foundElement = true;

            //check all fields
            Assert.assertNotEquals(ProducerField.ID.getName(), "", jsonSession.getString(ProducerField.ID.getName()));
            Assert.assertNotEquals(ProducerField.SESSION.getName(), "", jsonSession.getString(ProducerField.SESSION.getName()));
            Assert.assertEquals(ProducerField.USER.getName(), "", jsonSession.getString(ProducerField.USER.getName()));
            Assert.assertNotEquals(ProducerField.PROTOCOL.getName(), "", jsonSession.getString(ProducerField.PROTOCOL.getName()));
            Assert.assertEquals(ProducerField.ADDRESS.getName(), topicName.toString(), jsonSession.getString(ProducerField.ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.LOCAL_ADDRESS.getName(), "", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.REMOTE_ADDRESS.getName(), "", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.CREATION_TIME.getName(), "", jsonSession.getString(ProducerField.CREATION_TIME.getName()));
         }

         //remove the subs and make sure producers still exist
         mysub1.close();
         mysub2.close();
         session1.unsubscribe("mysub1");
         session2.unsubscribe("mysub2");
         producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         array = (JsonArray) producersAsJsonObject.get("data");
         assertEquals(2 + extraProducers, array.size());
         Assert.assertTrue("Valid session not found", foundElement);
      }
   }

   @Test
   public void testListProducersFQQN() throws Exception {
      SimpleString queueName1 = new SimpleString("my_queue_one");
      SimpleString addressName1 = new SimpleString("my_address_one");
      SimpleString FQQNName = new SimpleString(addressName1 + "::" + queueName1);
      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator)) {

         ClientSession session1 = csf.createSession();
         ClientSession session2 = csf.createSession();

         ClientProducer producer1 = session1.createProducer(FQQNName);
         producer1.send(session1.createMessage(true));
         ClientProducer producer2 = session2.createProducer(FQQNName);
         producer2.send(session2.createMessage(true));

         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         Assert.assertEquals("number of producers returned from query", 2 + extraProducers, array.size());

         boolean foundElement = false;
         for (int i = 0; i < array.size(); i++) {

            JsonObject jsonSession = array.getJsonObject(i);
            if (jsonSession.getString("address").equals("activemq.management")) {
               continue;
            }

            foundElement = true;

            //check all fields
            Assert.assertNotEquals(ProducerField.ID.getName(), "", jsonSession.getString(ProducerField.ID.getName()));
            Assert.assertNotEquals(ProducerField.SESSION.getName(), "", jsonSession.getString(ProducerField.SESSION.getName()));
            Assert.assertEquals(ProducerField.USER.getName(), "", jsonSession.getString(ProducerField.USER.getName()));
            Assert.assertNotEquals(ProducerField.PROTOCOL.getName(), "", jsonSession.getString(ProducerField.PROTOCOL.getName()));
            Assert.assertEquals(ProducerField.ADDRESS.getName(), FQQNName.toString(), jsonSession.getString(ProducerField.ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.LOCAL_ADDRESS.getName(), "", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.REMOTE_ADDRESS.getName(), "", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()));
            Assert.assertNotEquals(ProducerField.CREATION_TIME.getName(), "", jsonSession.getString(ProducerField.CREATION_TIME.getName()));
         }

         session1.deleteQueue(queueName1);

         producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         array = (JsonArray) producersAsJsonObject.get("data");

         //make sure both producers still exist as we dont tie them to an address which would only be recreated on another send
         assertEquals(2 + extraProducers, array.size());

         Assert.assertTrue("Valid session not found", foundElement);
      }
   }

   @Test
   public void testListProducersMessageCountsJMSCore() throws Exception {
      testListProducersMessageCountsJMS(ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY)), "CORE");
   }

   @Test
   public void testListProducersMessageCountsAMQPJMS() throws Exception {
      testListProducersMessageCountsJMS(new JmsConnectionFactory("amqp://localhost:61616"), "AMQP");
   }

   @Test
   public void testListProducersMessageCountsOWJMS() throws Exception {
      testListProducersMessageCountsJMS(new org.apache.activemq.ActiveMQConnectionFactory("tcp://localhost:61616"), "OPENWIRE");
   }

   public void testListProducersMessageCountsJMS(ConnectionFactory connectionFactory, String protocol) throws Exception {
      String myQueue = "my_queue";
      SimpleString queueName1 = new SimpleString(myQueue);
      SimpleString addressName1 = new SimpleString(myQueue);


      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      int numMessages = 10;

      try (Connection connection = connectionFactory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         MessageProducer producer = session.createProducer(session.createQueue(myQueue));
         for (int i = 0; i < numMessages; i++) {
            javax.jms.Message message = session.createMessage();
            producer.send(message);
         }
         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         Assert.assertEquals("number of producers returned from query", 1 + extraProducers, array.size());

         JsonObject jsonSession = array.getJsonObject(0);

         //check all fields
         Assert.assertNotEquals(ProducerField.ID.getName(), "", jsonSession.getString(ProducerField.ID.getName()));
         Assert.assertNotEquals(ProducerField.SESSION.getName(), "", jsonSession.getString(ProducerField.SESSION.getName()));
         Assert.assertEquals(ProducerField.USER.getName(), "", jsonSession.getString(ProducerField.USER.getName()));
         Assert.assertEquals(ProducerField.PROTOCOL.getAlternativeName(), protocol, jsonSession.getString(ProducerField.PROTOCOL.getName()));
         Assert.assertEquals(ProducerField.ADDRESS.getName(), addressName1.toString(), jsonSession.getString(ProducerField.ADDRESS.getName()));
         Assert.assertNotEquals(ProducerField.LOCAL_ADDRESS.getName(), "", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()));
         Assert.assertNotEquals(ProducerField.REMOTE_ADDRESS.getName(), "", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()));
         Assert.assertNotEquals(ProducerField.CREATION_TIME.getName(), "", jsonSession.getString(ProducerField.CREATION_TIME.getName()));
         Assert.assertEquals(ProducerField.MESSAGE_SENT.getName(), numMessages, jsonSession.getInt(ProducerField.MESSAGE_SENT.getName()));
         Assert.assertNotEquals(ProducerField.LAST_PRODUCED_MESSAGE_ID.getName(), "", jsonSession.getString(ProducerField.LAST_PRODUCED_MESSAGE_ID.getName()));
      }
   }


   @Test
   public void testListProducersMessageCounts() throws Exception {
      SimpleString queueName1 = new SimpleString("my_queue_one");
      SimpleString addressName1 = new SimpleString("my_address_one");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      int numMessages = 10;


      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator)) {

         ClientSession session1 = csf.createSession();

         ClientProducer producer1 = session1.createProducer(addressName1);
         int messagesSize = 0;
         for (int i = 0; i < numMessages; i++) {
            ClientMessage message = session1.createMessage(true);
            producer1.send(message);
            messagesSize += message.getEncodeSize();
         }
         //bring back all producers
         String filterString = createJsonFilter("", "", "");
         String producersAsJsonString = serverControl.listProducers(filterString, 1, 50);
         JsonObject producersAsJsonObject = JsonUtil.readJsonObject(producersAsJsonString);
         JsonArray array = (JsonArray) producersAsJsonObject.get("data");

         Assert.assertEquals("number of producers returned from query", 1 + extraProducers, array.size());

         JsonObject jsonSession = array.getJsonObject(0);

         //check all fields
         Assert.assertNotEquals(ProducerField.ID.getName(), "", jsonSession.getString(ProducerField.ID.getName()));
         Assert.assertNotEquals(ProducerField.SESSION.getName(), "", jsonSession.getString(ProducerField.SESSION.getName()));
         Assert.assertEquals(ProducerField.CLIENT_ID.getName(), "", jsonSession.getString(ProducerField.CLIENT_ID.getName()));
         Assert.assertEquals(ProducerField.USER.getName(), "", jsonSession.getString(ProducerField.USER.getName()));
         Assert.assertNotEquals(ProducerField.PROTOCOL.getAlternativeName(), "", jsonSession.getString(ProducerField.PROTOCOL.getName()));
         Assert.assertEquals(ProducerField.ADDRESS.getName(), addressName1.toString(), jsonSession.getString(ProducerField.ADDRESS.getName()));
         Assert.assertNotEquals(ProducerField.LOCAL_ADDRESS.getName(), "", jsonSession.getString(ProducerField.LOCAL_ADDRESS.getName()));
         Assert.assertNotEquals(ProducerField.REMOTE_ADDRESS.getName(), "", jsonSession.getString(ProducerField.REMOTE_ADDRESS.getName()));
         Assert.assertNotEquals(ProducerField.CREATION_TIME.getName(), "", jsonSession.getString(ProducerField.CREATION_TIME.getName()));
         Assert.assertEquals(ProducerField.MESSAGE_SENT.getName(), numMessages, jsonSession.getInt(ProducerField.MESSAGE_SENT.getName()));
         Assert.assertEquals(ProducerField.MESSAGE_SENT_SIZE.getName(), messagesSize, jsonSession.getInt(ProducerField.MESSAGE_SENT_SIZE.getName()));
         Assert.assertEquals(ProducerField.LAST_PRODUCED_MESSAGE_ID.getName(), "", jsonSession.getString(ProducerField.LAST_PRODUCED_MESSAGE_ID.getName()));
      }
   }

   @Test
   public void testListProducersMessageCounts2() throws Exception {
      SimpleString queueName1 = new SimpleString("my_queue_one");
      SimpleString addressName1 = new SimpleString("my_address_one");

      ActiveMQServerControl serverControl = createManagementControl();

      server.addAddressInfo(new AddressInfo(addressName1, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(addressName1, RoutingType.ANYCAST, queueName1, null, false, false);
      } else {
         server.createQueue(new QueueConfiguration(queueName1).setAddress(addressName1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
      }

      int numMessages = 10;

      // create some consumers
      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator)) {

         ClientSession session1 = csf.createSession();

         ClientProducer producer1 = session1.createProducer(addressName1);
         int messagesSize = 0;
         for (int i = 0; i < numMessages; i++) {
            ClientMessage message = session1.createMessage(true);
            producer1.send(message);
            messagesSize += message.getEncodeSize();
         }
         //bring back all producers
         String producersAsJsonString = serverControl.listProducersInfoAsJSON();
         JsonArray jsonArray = JsonUtil.readJsonArray(producersAsJsonString);

         JsonObject jsonSession = jsonArray.getJsonObject(0);

         if (!jsonSession.getString(ProducerField.ADDRESS.getAlternativeName()).equalsIgnoreCase(addressName1.toString())) {
            jsonSession = jsonArray.getJsonObject(1);
         }

         //check all fields
         Assert.assertNotEquals(ProducerField.ID.getName(), "", jsonSession.getString(ProducerField.ID.getName()));
         Assert.assertNotEquals(ProducerField.NAME.getName(), "", jsonSession.getString(ProducerField.NAME.getName()));
         Assert.assertNotEquals(ProducerField.SESSION.getAlternativeName(), "", jsonSession.getString(ProducerField.SESSION.getAlternativeName()));
         Assert.assertNotEquals(ProducerField.CREATION_TIME.getName(), "", jsonSession.getString(ProducerField.CREATION_TIME.getName()));
         Assert.assertEquals(ProducerField.MESSAGE_SENT.getName(), numMessages, jsonSession.getInt(ProducerField.MESSAGE_SENT.getName()));
         Assert.assertEquals(ProducerField.MESSAGE_SENT_SIZE.getName(), messagesSize, jsonSession.getInt(ProducerField.MESSAGE_SENT_SIZE.getName()));
         Assert.assertEquals(ProducerField.LAST_PRODUCED_MESSAGE_ID.getName(), JsonValue.NULL, jsonSession.get(ProducerField.LAST_PRODUCED_MESSAGE_ID.getName()));
      }
   }

   @Test
   public void testMemoryUsagePercentage() throws Exception {
      //messages size 100K
      final int MESSAGE_SIZE = 100000;
      String name1 = "messageUsagePercentage.test.1";

      server.stop();
      //no globalMaxSize set
      server.getConfiguration().setGlobalMaxSize(-1);
      server.start();

      ActiveMQServerControl serverControl = createManagementControl();
      // check before adding messages
      assertEquals("Memory Usage before adding messages", 0, serverControl.getAddressMemoryUsage());
      assertEquals("MemoryUsagePercentage", 0, serverControl.getAddressMemoryUsagePercentage());

      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator);
           ClientSession session = csf.createSession()) {
         if (legacyCreateQueue) {
            session.createQueue(name1, RoutingType.ANYCAST, name1);
         } else {
            session.createQueue(new QueueConfiguration(name1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         }

         Queue serverQueue = server.locateQueue(SimpleString.toSimpleString(name1));
         Assert.assertFalse(serverQueue.isDurable());
         ClientProducer producer1 = session.createProducer(name1);
         sendMessagesWithPredefinedSize(30, session, producer1, MESSAGE_SIZE);

         //it is hard to predict an exact number so checking if it falls in a certain range: totalSizeOfMessageSent < X > totalSizeofMessageSent + 100k
         assertTrue("Memory Usage within range ", ((30 * MESSAGE_SIZE) < serverControl.getAddressMemoryUsage()) && (serverControl.getAddressMemoryUsage() < ((30 * MESSAGE_SIZE) + 100000)));
         // no globalMaxSize set so it should return zero
         assertEquals("MemoryUsagePercentage", 0, serverControl.getAddressMemoryUsagePercentage());
      }
   }

   @Test
   public void testMemoryUsage() throws Exception {
      //messages size 100K
      final int MESSAGE_SIZE = 100000;
      String name1 = "messageUsage.test.1";
      String name2 = "messageUsage.test.2";

      server.stop();
      // set to 5 MB
      server.getConfiguration().setGlobalMaxSize(5000000);
      server.start();

      ActiveMQServerControl serverControl = createManagementControl();
      // check before adding messages
      assertEquals("Memory Usage before adding messages", 0, serverControl.getAddressMemoryUsage());
      assertEquals("MemoryUsagePercentage", 0, serverControl.getAddressMemoryUsagePercentage());

      try (ServerLocator locator = createInVMNonHALocator();
           ClientSessionFactory csf = createSessionFactory(locator);
           ClientSession session = csf.createSession()) {
         if (legacyCreateQueue) {
            session.createQueue(name1, RoutingType.ANYCAST, name1);
            session.createQueue(name2, RoutingType.ANYCAST, name2);
         } else {
            session.createQueue(new QueueConfiguration(name1).setRoutingType(RoutingType.ANYCAST).setDurable(false));
            session.createQueue(new QueueConfiguration(name2).setRoutingType(RoutingType.ANYCAST).setDurable(false));
         }

         Queue serverQueue = server.locateQueue(SimpleString.toSimpleString(name1));
         Assert.assertFalse(serverQueue.isDurable());

         ClientProducer producer1 = session.createProducer(name1);
         ClientProducer producer2 = session.createProducer(name2);
         sendMessagesWithPredefinedSize(10, session, producer1, MESSAGE_SIZE);
         sendMessagesWithPredefinedSize(10, session, producer2, MESSAGE_SIZE);

         //it is hard to predict an exact number so checking if it falls in a certain range: totalSizeOfMessageSent < X > totalSizeofMessageSent + 100k
         assertTrue("Memory Usage within range ", ((20 * MESSAGE_SIZE) < serverControl.getAddressMemoryUsage()) && (serverControl.getAddressMemoryUsage() < ((20 * MESSAGE_SIZE) + 100000)));
         assertTrue("MemoryUsagePercentage", (40 <= serverControl.getAddressMemoryUsagePercentage()) && (42 >= serverControl.getAddressMemoryUsagePercentage()));
      }
   }

   @Test
   public void testConnectorServiceManagement() throws Exception {
      ActiveMQServerControl managementControl = createManagementControl();
      managementControl.createConnectorService("myconn", FakeConnectorServiceFactory.class.getCanonicalName(), new HashMap<String, Object>());

      Assert.assertEquals(1, server.getConnectorsService().getConnectors().size());

      managementControl.createConnectorService("myconn2", FakeConnectorServiceFactory.class.getCanonicalName(), new HashMap<String, Object>());
      Assert.assertEquals(2, server.getConnectorsService().getConnectors().size());

      managementControl.destroyConnectorService("myconn");
      Assert.assertEquals(1, server.getConnectorsService().getConnectors().size());
      Assert.assertEquals("myconn2", managementControl.getConnectorServices()[0]);
   }

   @Test
   public void testCloseCOREclient() throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString name = RandomUtil.randomSimpleString();
      boolean durable = true;

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      serverControl.createAddress(address.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), null, durable, -1, false, false);
      } else {
         serverControl.createQueue(new QueueConfiguration(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(durable).setAutoCreateAddress(false).toJSON());
      }

      ServerLocator receiveLocator = createInVMNonHALocator();
      ClientSessionFactory receiveCsf = createSessionFactory(receiveLocator);
      ClientSession receiveClientSession = receiveCsf.createSession(true, false, false);
      final ClientConsumer COREclient = receiveClientSession.createConsumer(name);

      ServerSession ss = server.getSessions().iterator().next();
      ServerConsumer sc = ss.getServerConsumers().iterator().next();

      Assert.assertFalse(COREclient.isClosed());
      serverControl.closeConsumerWithID(((ClientSessionImpl)receiveClientSession).getName(), Long.toString(sc.sequentialID()));
      Wait.waitFor(() -> COREclient.isClosed());
      Assert.assertTrue(COREclient.isClosed());
   }

   @Test
   public void testCloseJMSclient() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      ConnectionFactory cf = ActiveMQJMSClient.createConnectionFactoryWithoutHA(JMSFactoryType.CF, new TransportConfiguration(INVM_CONNECTOR_FACTORY));
      Connection conn = cf.createConnection();
      conn.start();
      Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
      javax.jms.Topic topic = ActiveMQJMSClient.createTopic("ConsumerTestTopic");

      MessageConsumer JMSclient = session.createConsumer(topic, "test1");


      long clientID = -1;
      String sessionID = ((ClientSessionImpl)(((ActiveMQSession)session).getCoreSession())).getName();

      Set<ServerSession> sessions = server.getSessions();
      for (ServerSession sess : sessions) {
         if (sess.getName().equals(sessionID.toString())) {
            Set<ServerConsumer> serverConsumers = sess.getServerConsumers();
            for (ServerConsumer serverConsumer : serverConsumers) {
               clientID = serverConsumer.sequentialID();
            }
         }
      }

      Assert.assertFalse(((org.apache.activemq.artemis.jms.client.ActiveMQMessageConsumer)JMSclient).isClosed());
      serverControl.closeConsumerWithID(sessionID, Long.toString(clientID));
      Wait.waitFor(() -> ((org.apache.activemq.artemis.jms.client.ActiveMQMessageConsumer)JMSclient).isClosed());
      Assert.assertTrue(((org.apache.activemq.artemis.jms.client.ActiveMQMessageConsumer)JMSclient).isClosed());
   }

   @Test
   public void testForceCloseSession() throws Exception {
      testForceCloseSession(false, false);
   }

   @Test
   public void testForceCloseSessionWithError() throws Exception {
      testForceCloseSession(true, false);
   }

   @Test
   public void testForceCloseSessionWithPendingStoreOperation() throws Exception {
      testForceCloseSession(false, true);
   }

   private void testForceCloseSession(boolean error, boolean pendingStoreOperation) throws Exception {
      SimpleString address = RandomUtil.randomSimpleString();
      SimpleString name = RandomUtil.randomSimpleString();
      boolean durable = true;

      ActiveMQServerControl serverControl = createManagementControl();

      checkNoResource(ObjectNameBuilder.DEFAULT.getQueueObjectName(address, name, RoutingType.ANYCAST));
      serverControl.createAddress(address.toString(), "ANYCAST");
      if (legacyCreateQueue) {
         serverControl.createQueue(address.toString(), "ANYCAST", name.toString(), null, durable, -1, false, false);
      } else {
         serverControl.createQueue(new QueueConfiguration(name).setAddress(address).setRoutingType(RoutingType.ANYCAST).setDurable(durable).setAutoCreateAddress(false).toJSON());
      }

      ServerLocator receiveLocator = createInVMNonHALocator().setCallTimeout(500);
      ClientSessionFactory receiveCsf = createSessionFactory(receiveLocator);
      ClientSession receiveClientSession = receiveCsf.createSession(true, false, false);
      final ClientConsumer clientConsumer = receiveClientSession.createConsumer(name);

      Assert.assertEquals(1, server.getSessions().size());

      ServerSession serverSession = server.getSessions().iterator().next();
      Assert.assertEquals(((ClientSessionImpl)receiveClientSession).getName(), serverSession.getName());

      if (error) {
         serverSession.getSessionContext().onError(0, "error");
      }

      if (pendingStoreOperation) {
         serverSession.getSessionContext().storeLineUp();
      }

      serverControl.closeSessionWithID(serverSession.getConnectionID().toString(), serverSession.getName(), true);

      Wait.assertTrue(() -> serverSession.getServerConsumers().size() == 0, 500);
      Wait.assertTrue(() -> server.getSessions().size() == 0, 500);
   }

   @Test
   public void testAddUser() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      try {
         serverControl.addUser("x", "x", "x", true);
         fail();
      } catch (Exception expected) {
      }
   }

   @Test
   public void testRemoveUser() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      try {
         serverControl.removeUser("x");
         fail();
      } catch (Exception expected) {
      }
   }

   @Test
   public void testListUser() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      try {
         serverControl.listUser("x");
         fail();
      } catch (Exception expected) {
      }
   }

   @Test
   public void testResetUser() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      try {
         serverControl.resetUser("x","x","x");
         fail();
      } catch (Exception expected) {
      }
   }

   @Test
   public void testReplayWithoutDate() throws Exception {
      testReplaySimple(false);
   }

   @Test
   public void testReplayWithDate() throws Exception {
      testReplaySimple(true);
   }

   private void testReplaySimple(boolean useDate) throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      String queue = "testQueue" + RandomUtil.randomString();
      server.addAddressInfo(new AddressInfo(queue).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(new QueueConfiguration(queue).setRoutingType(RoutingType.ANYCAST).setAddress(queue));

      ConnectionFactory factory = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue jmsQueue = session.createQueue(queue);
         MessageProducer producer = session.createProducer(jmsQueue);
         producer.send(session.createTextMessage("before"));

         connection.start();
         MessageConsumer consumer = session.createConsumer(jmsQueue);
         Assert.assertNotNull(consumer.receive(5000));
         Assert.assertNull(consumer.receiveNoWait());

         serverControl.replay(queue, queue, null);
         Assert.assertNotNull(consumer.receive(5000));
         Assert.assertNull(consumer.receiveNoWait());

         if (useDate) {
            serverControl.replay("dontexist", "dontexist", null); // just to force a move next file, and copy stuff into place
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
            Thread.sleep(1000); // waiting a second just to have the timestamp change
            String dateEnd = format.format(new Date());
            Thread.sleep(1000); // waiting a second just to have the timestamp change
            String dateStart = "19800101000000";


            for (int i = 0; i < 100; i++) {
               producer.send(session.createTextMessage("after receiving"));
            }
            for (int i = 0; i < 100; i++) {
               Assert.assertNotNull(consumer.receive());
            }
            Assert.assertNull(consumer.receiveNoWait());
            serverControl.replay(dateStart, dateEnd, queue, queue, null);
            for (int i = 0; i < 2; i++) { // replay of the replay will contain two messages
               TextMessage message = (TextMessage) consumer.receive(5000);
               Assert.assertNotNull(message);
               Assert.assertEquals("before", message.getText());
            }
            Assert.assertNull(consumer.receiveNoWait());
         } else {
            serverControl.replay(queue, queue, null);

            // replay of the replay, there will be two messages
            for (int i = 0; i < 2; i++) {
               Assert.assertNotNull(consumer.receive(5000));
            }
            Assert.assertNull(consumer.receiveNoWait());
         }
      }
   }


   @Test
   public void testReplayFilter() throws Exception {
      ActiveMQServerControl serverControl = createManagementControl();
      String queue = "testQueue" + RandomUtil.randomString();
      server.addAddressInfo(new AddressInfo(queue).addRoutingType(RoutingType.ANYCAST));
      server.createQueue(new QueueConfiguration(queue).setRoutingType(RoutingType.ANYCAST).setAddress(queue));

      ConnectionFactory factory = CFUtil.createConnectionFactory("core", "tcp://localhost:61616");
      try (Connection connection = factory.createConnection()) {
         Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
         javax.jms.Queue jmsQueue = session.createQueue(queue);
         MessageProducer producer = session.createProducer(jmsQueue);
         for (int i = 0; i < 10; i++) {
            TextMessage message = session.createTextMessage("message " + i);
            message.setIntProperty("i", i);
            producer.send(message);
         }

         connection.start();
         MessageConsumer consumer = session.createConsumer(jmsQueue);
         for (int i = 0; i < 10; i++) {
            Assert.assertNotNull(consumer.receive(5000));
         }
         Assert.assertNull(consumer.receiveNoWait());

         serverControl.replay(queue, queue, "i=5");
         TextMessage message = (TextMessage)consumer.receive(5000);
         Assert.assertNotNull(message);
         Assert.assertEquals(5, message.getIntProperty("i"));
         Assert.assertEquals("message 5", message.getText());
         Assert.assertNull(consumer.receiveNoWait());
      }
   }


   @Test
   public void testBrokerConnections() throws Exception {
      class Fake implements BrokerConnection {
         String name;
         boolean started = false;

         Fake(String name) {
            this.name = name;
         }
         @Override
         public String getName() {
            return name;
         }

         @Override
         public String getProtocol() {
            return "fake";
         }

         @Override
         public void start() throws Exception {
            started = true;
         }

         @Override
         public void stop() throws Exception {
            started = false;

         }

         @Override
         public boolean isStarted() {
            return started;
         }
      }
      Fake fake = new Fake("fake" + UUIDGenerator.getInstance().generateStringUUID());
      server.registerBrokerConnection(fake);

      ActiveMQServerControl serverControl = createManagementControl();
      try {
         String result = serverControl.listBrokerConnections();
         Assert.assertTrue(result.contains(fake.getName()));
         serverControl.startBrokerConnection(fake.getName());
         Assert.assertTrue(fake.isStarted());
         serverControl.stopBrokerConnection(fake.getName());
         Assert.assertFalse(fake.isStarted());
      } catch (Exception expected) {
      }
   }

   @Test
   public void testManualStopStartEmbeddedWebServer() throws Exception {
      FakeWebServerComponent fake = new FakeWebServerComponent();
      server.addExternalComponent(fake, true);
      Assert.assertTrue(fake.isStarted());

      ActiveMQServerControl serverControl = createManagementControl();
      serverControl.stopEmbeddedWebServer();
      Assert.assertFalse(fake.isStarted());
      serverControl.startEmbeddedWebServer();
      Assert.assertTrue(fake.isStarted());
   }

   @Test
   public void testRestartEmbeddedWebServer() throws Exception {
      FakeWebServerComponent fake = new FakeWebServerComponent();
      server.addExternalComponent(fake, true);
      Assert.assertTrue(fake.isStarted());

      ActiveMQServerControl serverControl = createManagementControl();
      long time = System.currentTimeMillis();
      Assert.assertTrue(time >= fake.getStartTime());
      Assert.assertTrue(time > fake.getStopTime());
      Thread.sleep(5);
      serverControl.restartEmbeddedWebServer();
      Assert.assertTrue(serverControl.isEmbeddedWebServerStarted());
      Assert.assertTrue(time < fake.getStartTime());
      Assert.assertTrue(time < fake.getStopTime());
   }

   @Test
   public void testRestartEmbeddedWebServerTimeout() throws Exception {
      final CountDownLatch startDelay = new CountDownLatch(1);
      FakeWebServerComponent fake = new FakeWebServerComponent(startDelay);
      server.addExternalComponent(fake, false);

      ActiveMQServerControl serverControl = createManagementControl();
      try {
         serverControl.restartEmbeddedWebServer(1);
         fail();
      } catch (ActiveMQTimeoutException e) {
         // expected
      } finally {
         startDelay.countDown();
      }
      Wait.waitFor(() -> fake.isStarted());
   }

   @Test
   public void testRestartEmbeddedWebServerException() throws Exception {
      final String message = RandomUtil.randomString();
      final Exception startException = new ActiveMQIllegalStateException(message);
      FakeWebServerComponent fake = new FakeWebServerComponent(startException);
      server.addExternalComponent(fake, false);

      ActiveMQServerControl serverControl = createManagementControl();
      try {
         serverControl.restartEmbeddedWebServer(1000);
         fail();
      } catch (ActiveMQException e) {
         assertSame("Unexpected cause", startException, e.getCause());
      }
   }

   class FakeWebServerComponent implements ServiceComponent, WebServerComponentMarker {
      AtomicBoolean started = new AtomicBoolean(false);
      AtomicLong startTime = new AtomicLong(0);
      AtomicLong stopTime = new AtomicLong(0);
      CountDownLatch startDelay;
      Exception startException;

      FakeWebServerComponent(CountDownLatch startDelay) {
         this.startDelay = startDelay;
      }

      FakeWebServerComponent(Exception startException) {
         this.startException = startException;
      }

      FakeWebServerComponent() {
      }

      @Override
      public void start() throws Exception {
         if (started.get()) {
            return;
         }
         if (startDelay != null) {
            startDelay.await();
         }
         if (startException != null) {
            throw startException;
         }
         startTime.set(System.currentTimeMillis());
         started.set(true);
      }

      @Override
      public void stop() throws Exception {
         stop(false);
      }

      @Override
      public void stop(boolean shutdown) throws Exception {
         if (!shutdown) {
            throw new RuntimeException("shutdown flag must be true");
         }
         stopTime.set(System.currentTimeMillis());
         started.set(false);
      }

      @Override
      public boolean isStarted() {
         return started.get();
      }

      public long getStartTime() {
         return startTime.get();
      }

      public long getStopTime() {
         return stopTime.get();
      }
   }

   protected void scaleDown(ScaleDownHandler handler) throws Exception {
      SimpleString address = new SimpleString("testQueue");
      HashMap<String, Object> params = new HashMap<>();
      params.put(TransportConstants.SERVER_ID_PROP_NAME, "2");
      Configuration config = createDefaultInVMConfig(2).clearAcceptorConfigurations().addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getName(), params)).setSecurityEnabled(false);
      ActiveMQServer server2 = addServer(ActiveMQServers.newActiveMQServer(config, null, true));

      this.conf.clearConnectorConfigurations().addConnectorConfiguration("server2-connector", new TransportConfiguration(INVM_CONNECTOR_FACTORY, params));

      server2.start();
      server.addAddressInfo(new AddressInfo(address, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server.createQueue(address, RoutingType.ANYCAST, address, null, true, false, -1, false, false);
      } else {
         server.createQueue(new QueueConfiguration(address).setRoutingType(RoutingType.ANYCAST).setAutoCreateAddress(false));
      }
      server2.addAddressInfo(new AddressInfo(address, RoutingType.ANYCAST));
      if (legacyCreateQueue) {
         server2.createQueue(address, RoutingType.ANYCAST, address, null, true, false, -1, false, false);
      } else {
         server2.createQueue(new QueueConfiguration(address).setRoutingType(RoutingType.ANYCAST).setAutoCreateAddress(false));
      }
      ServerLocator locator = createInVMNonHALocator();
      ClientSessionFactory csf = createSessionFactory(locator);
      ClientSession session = csf.createSession();
      ClientProducer producer = session.createProducer(address);
      for (int i = 0; i < 100; i++) {
         ClientMessage message = session.createMessage(true);
         message.getBodyBuffer().writeString("m" + i);
         producer.send(message);
      }

      ActiveMQServerControl managementControl = createManagementControl();
      handler.scaleDown(managementControl);
      locator.close();
      locator = addServerLocator(ActiveMQClient.createServerLocatorWithoutHA(new TransportConfiguration(INVM_CONNECTOR_FACTORY, params)));
      csf = createSessionFactory(locator);
      session = csf.createSession();
      session.start();
      ClientConsumer consumer = session.createConsumer(address);
      for (int i = 0; i < 100; i++) {
         ClientMessage m = consumer.receive(5000);
         assertNotNull(m);
      }
   }

   interface ScaleDownHandler {

      void scaleDown(ActiveMQServerControl control) throws Exception;
   }

   @Override
   @Before
   public void setUp() throws Exception {
      super.setUp();

      connectorConfig = new TransportConfiguration(INVM_CONNECTOR_FACTORY);

      conf = createDefaultNettyConfig().setJMXManagementEnabled(true).addConnectorConfiguration(connectorConfig.getName(), connectorConfig);
      conf.setSecurityEnabled(true);
      SecurityConfiguration securityConfiguration = new SecurityConfiguration();
      securityConfiguration.addUser("guest", "guest");
      securityConfiguration.addUser("myUser", "myPass");
      securityConfiguration.addRole("guest", "guest");
      securityConfiguration.addRole("myUser", "guest");
      securityConfiguration.setDefaultUser("guest");
      ActiveMQJAASSecurityManager securityManager = new ActiveMQJAASSecurityManager(InVMLoginModule.class.getName(), securityConfiguration);
      conf.setJournalRetentionDirectory(conf.getJournalDirectory() + "_ret"); // needed for replay tests
      server = addServer(ActiveMQServers.newActiveMQServer(conf, mbeanServer, securityManager, true));
      server.getConfiguration().setAddressQueueScanPeriod(100);
      server.start();

      HashSet<Role> role = new HashSet<>();
      role.add(new Role("guest", true, true, true, true, true, true, true, true, true, true));
      server.getSecurityRepository().addMatch("#", role);
   }

   protected ActiveMQServerControl createManagementControl() throws Exception {
      return ManagementControlHelper.createActiveMQServerControl(mbeanServer);
   }

   private String createJsonFilter(String fieldName, String operationName, String value,String sortColumn, String sortOrder) {
      HashMap<String, Object> filterMap = new HashMap<>();
      filterMap.put("field", fieldName);
      filterMap.put("operation", operationName);
      filterMap.put("value", value);
      filterMap.put("sortColumn", sortColumn);
      filterMap.put("sortOrder", sortOrder);
      JsonObject jsonFilterObject = JsonUtil.toJsonObject(filterMap);
      return jsonFilterObject.toString();
   }

   private void sendMessagesWithPredefinedSize(int numberOfMessages,
                                               ClientSession session,
                                               ClientProducer producer,
                                               int messageSize) throws Exception {
      ClientMessage message;
      final byte[] body = new byte[messageSize];
      ByteBuffer bb = ByteBuffer.wrap(body);
      for (int i = 1; i <= messageSize; i++) {
         bb.put(getSamplebyte(i));
      }

      for (int i = 0; i < numberOfMessages; i++) {
         message = session.createMessage(true);
         ActiveMQBuffer bodyLocal = message.getBodyBuffer();
         bodyLocal.writeBytes(body);

         producer.send(message);
         if (i % 1000 == 0) {
            session.commit();
         }
      }
      session.commit();
   }

}

