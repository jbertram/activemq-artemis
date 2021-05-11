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

package org.apache.activemq.artemis.tests.integration.mqtt5;

import javax.jms.ConnectionFactory;
import java.io.File;
import java.io.IOException;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.postoffice.Binding;
import org.apache.activemq.artemis.core.postoffice.impl.LocalQueueBinding;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTInterceptor;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTProtocolManager;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTSessionState;
import org.apache.activemq.artemis.core.protocol.mqtt.MQTTUtil;
import org.apache.activemq.artemis.core.remoting.impl.AbstractAcceptor;
import org.apache.activemq.artemis.core.remoting.impl.netty.TransportConstants;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.spi.core.protocol.ProtocolManager;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.Acceptor;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttClient;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static java.util.Collections.singletonList;
import static org.apache.activemq.artemis.core.protocol.mqtt.MQTTProtocolManagerFactory.MQTT_PROTOCOL_NAME;

@RunWith(Parameterized.class)
public class MQTT5TestSupport extends ActiveMQTestBase {
   protected static final String TCP = "tcp";
   protected static final String WS = "ws";
   protected static final SimpleString DEAD_LETTER_ADDRESS = new SimpleString("DLA");
   protected static final SimpleString EXPIRY_ADDRESS = new SimpleString("EXPIRY");

   @Parameterized.Parameters(name = "protocol={0}")
   public static Collection<Object[]> getParams() {
      return Arrays.asList(new Object[][] {
         {TCP},
         {WS}
      });
   }

   protected String protocol;

   public MQTT5TestSupport(String protocol) {
      this.protocol = protocol;
   }

   protected MqttClient createPahoClient(String clientId) throws MqttException {
      return new MqttClient(protocol + "://localhost:" + getPort(), clientId, new MemoryPersistence());
   }

   protected MqttAsyncClient createAsyncPahoClient(String clientId) throws MqttException {
      return new MqttAsyncClient(protocol + "://localhost:" + getPort(), clientId, new MemoryPersistence());
   }

   private static final Logger log = Logger.getLogger(MQTT5TestSupport.class);
   protected static final long DEFAULT_TIMEOUT = 300000;
   protected ActiveMQServer server;

   protected int port = 1883;
   protected ConnectionFactory cf;
   protected LinkedList<Throwable> exceptions = new LinkedList<>();
   protected boolean persistent;
   protected String protocolScheme;
   protected boolean useSSL;

   protected static final int NUM_MESSAGES = 250;

   public static final int AT_MOST_ONCE = 0;
   public static final int AT_LEAST_ONCE = 1;
   public static final int EXACTLY_ONCE = 2;

   protected String noprivUser = "noprivs";
   protected String noprivPass = "noprivs";

   protected String browseUser = "browser";
   protected String browsePass = "browser";

   protected String guestUser = "guest";
   protected String guestPass = "guest";

   protected String fullUser = "user";
   protected String fullPass = "pass";

   @Rule
   public TestName name = new TestName();

   public MQTT5TestSupport() {
      this.protocolScheme = "mqtt";
      this.useSSL = false;
   }

   public File basedir() throws IOException {
      ProtectionDomain protectionDomain = getClass().getProtectionDomain();
      return new File(new File(protectionDomain.getCodeSource().getLocation().getPath()), "../..").getCanonicalFile();
   }

   @Override
   public String getName() {
      return name.getMethodName();
   }

   public ActiveMQServer getServer() {
      return server;
   }

   @Override
   @Before
   public void setUp() throws Exception {
      String basedir = basedir().getPath();
      System.setProperty("javax.net.ssl.trustStore", basedir + "/src/test/resources/client.keystore");
      System.setProperty("javax.net.ssl.trustStorePassword", "password");
      System.setProperty("javax.net.ssl.trustStoreType", "jks");
      System.setProperty("javax.net.ssl.keyStore", basedir + "/src/test/resources/server.keystore");
      System.setProperty("javax.net.ssl.keyStorePassword", "password");
      System.setProperty("javax.net.ssl.keyStoreType", "jks");

      exceptions.clear();
      startBroker();
      createJMSConnection();
      org.jboss.logmanager.Logger.getLogger(MQTTUtil.class.getName()).setLevel(org.jboss.logmanager.Level.TRACE);
   }

   @Override
   @After
   public void tearDown() throws Exception {
      System.clearProperty("javax.net.ssl.trustStore");
      System.clearProperty("javax.net.ssl.trustStorePassword");
      System.clearProperty("javax.net.ssl.trustStoreType");
      System.clearProperty("javax.net.ssl.keyStore");
      System.clearProperty("javax.net.ssl.keyStorePassword");
      System.clearProperty("javax.net.ssl.keyStoreType");
      stopBroker();
      super.tearDown();
   }

   public void configureBroker() throws Exception {
      super.setUp();
      server = createServerForMQTT();
      addCoreConnector();
      addMQTTConnector();
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setMaxSizeBytes(999999999);
      addressSettings.setAutoCreateQueues(true);
      addressSettings.setAutoCreateAddresses(true);
      configureBrokerSecurity(server);

      server.getAddressSettingsRepository().addMatch("#", addressSettings);

      server.getConfiguration().setMessageExpiryScanPeriod(500);
   }

   /**
    * Copied from org.apache.activemq.artemis.tests.integration.amqp.AmqpClientTestSupport#configureBrokerSecurity()
    */
   protected void configureBrokerSecurity(ActiveMQServer server) {
      if (isSecurityEnabled()) {
         ActiveMQJAASSecurityManager securityManager = (ActiveMQJAASSecurityManager) server.getSecurityManager();

         // User additions
         securityManager.getConfiguration().addUser(noprivUser, noprivPass);
         securityManager.getConfiguration().addRole(noprivUser, "nothing");
         securityManager.getConfiguration().addUser(browseUser, browsePass);
         securityManager.getConfiguration().addRole(browseUser, "browser");
         securityManager.getConfiguration().addUser(guestUser, guestPass);
         securityManager.getConfiguration().addRole(guestUser, "guest");
         securityManager.getConfiguration().addUser(fullUser, fullPass);
         securityManager.getConfiguration().addRole(fullUser, "full");

         // Configure roles
         HierarchicalRepository<Set<Role>> securityRepository = server.getSecurityRepository();
         HashSet<Role> value = new HashSet<>();
         value.add(new Role("nothing", false, false, false, false, false, false, false, false, false, false));
         value.add(new Role("browser", false, false, false, false, false, false, false, true, false, false));
         value.add(new Role("guest", false, true, false, false, false, false, false, true, false, false));
         value.add(new Role("full", true, true, true, true, true, true, true, true, true, true));
         securityRepository.addMatch("#", value);

         server.getConfiguration().setSecurityEnabled(true);
      } else {
         server.getConfiguration().setSecurityEnabled(false);
      }
   }

   public void startBroker() throws Exception {
      configureBroker();
      server.start();
      server.waitForActivation(10, TimeUnit.SECONDS);
   }

   public void createJMSConnection() throws Exception {
      cf = new ActiveMQConnectionFactory(false, new TransportConfiguration(ActiveMQTestBase.NETTY_CONNECTOR_FACTORY));
   }

   private ActiveMQServer createServerForMQTT() throws Exception {
      Configuration defaultConfig = createDefaultConfig(true).setIncomingInterceptorClassNames(singletonList(MQTTIncomingInterceptor.class.getName())).setOutgoingInterceptorClassNames(singletonList(MQTTOutoingInterceptor.class.getName()));
      AddressSettings addressSettings = new AddressSettings();
      addressSettings.setDeadLetterAddress(DEAD_LETTER_ADDRESS);
      addressSettings.setExpiryAddress(EXPIRY_ADDRESS);
      defaultConfig.getAddressesSettings().put("#", addressSettings);
      defaultConfig.setMqttSessionScanInterval(200);
      return createServer(true, defaultConfig);
   }

   protected void addCoreConnector() throws Exception {
      // Overrides of this method can add additional configuration options or add multiple
      // MQTT transport connectors as needed, the port variable is always supposed to be
      // assigned the primary MQTT connector's port.

      Map<String, Object> params = new HashMap<>();
      params.put(TransportConstants.PORT_PROP_NAME, "" + 5445);
      params.put(TransportConstants.PROTOCOLS_PROP_NAME, "CORE");
      TransportConfiguration transportConfiguration = new TransportConfiguration(NETTY_ACCEPTOR_FACTORY, params);
      server.getConfiguration().getAcceptorConfigurations().add(transportConfiguration);

      log.debug("Added CORE connector to broker");
   }

   protected void addMQTTConnector() throws Exception {
      // Overrides of this method can add additional configuration options or add multiple
      // MQTT transport connectors as needed, the port variable is always supposed to be
      // assigned the primary MQTT connector's port.

      server.getConfiguration().addAcceptorConfiguration(MQTT_PROTOCOL_NAME, "tcp://localhost:" + port + "?protocols=MQTT;anycastPrefix=anycast:;multicastPrefix=multicast:");
      server.getConfiguration().setConnectionTtlCheckInterval(100);

      log.debug("Added MQTT connector to broker");
   }

   public void stopBroker() throws Exception {
      if (server.isStarted()) {
         server.stop();
         server = null;
      }
   }

   protected String getQueueName() {
      return getClass().getName() + "." + name.getMethodName();
   }

   protected String getTopicName() {
      return getClass().getName() + "." + name.getMethodName();
   }

   public boolean isPersistent() {
      return persistent;
   }

   public int getPort() {
      return this.port;
   }

   public boolean isSecurityEnabled() {
      return false;
   }

   protected interface Task {

      void run() throws Exception;
   }

   public Map<String, MQTTSessionState> getSessionStates() {
      Acceptor acceptor = server.getRemotingService().getAcceptor("MQTT");
      if (acceptor instanceof AbstractAcceptor) {
         ProtocolManager protocolManager = ((AbstractAcceptor) acceptor).getProtocolMap().get("MQTT");
         if (protocolManager instanceof MQTTProtocolManager) {
            return ((MQTTProtocolManager) protocolManager).getSessionStates();
         }

      }
      return Collections.emptyMap();
   }

   protected Queue getSubscriptionQueue(String TOPIC) {
      try {
         return ((LocalQueueBinding)server.getPostOffice().getBindingsForAddress(SimpleString.toSimpleString(TOPIC)).getBindings().toArray()[0]).getQueue();
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   protected Queue getSubscriptionQueue(String TOPIC, String clientId) {
      try {
         for (Binding b : server.getPostOffice().getMatchingBindings(SimpleString.toSimpleString(TOPIC))) {
            if (((LocalQueueBinding)b).getQueue().getName().startsWith(SimpleString.toSimpleString(clientId))) {
               return ((LocalQueueBinding)b).getQueue();
            }
         }
         return null;
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   protected void setAcceptorProperty(String property) throws Exception {
      server.getRemotingService().getAcceptor(MQTT_PROTOCOL_NAME).stop();
      server.getRemotingService().createAcceptor(MQTT_PROTOCOL_NAME, "tcp://localhost:" + port + "?protocols=MQTT;" + property).start();
   }

   public static class MQTTIncomingInterceptor implements MQTTInterceptor {

      private static int messageCount = 0;

      @Override
      public boolean intercept(MqttMessage packet, RemotingConnection connection) throws ActiveMQException {
         if (packet.getClass() == MqttPublishMessage.class) {
            messageCount++;
         }
         return true;
      }

      public static void clear() {
         messageCount = 0;
      }

      public static int getMessageCount() {
         return messageCount;
      }
   }

   public static class MQTTOutoingInterceptor implements MQTTInterceptor {

      private static int messageCount = 0;

      @Override
      public boolean intercept(MqttMessage packet, RemotingConnection connection) throws ActiveMQException {
         if (packet.getClass() == MqttPublishMessage.class) {
            messageCount++;
         }
         return true;
      }

      public static void clear() {
         messageCount = 0;
      }

      public static int getMessageCount() {
         return messageCount;
      }
   }

   protected interface DefaultMqttCallback extends MqttCallback {
      @Override
      default void disconnected(MqttDisconnectResponse disconnectResponse) {
      }

      @Override
      default void mqttErrorOccurred(MqttException exception) {
      }

      @Override
      default void messageArrived(String topic, org.eclipse.paho.mqttv5.common.MqttMessage message) throws Exception {
      }

      @Override
      default void deliveryComplete(IMqttToken token) {
      }

      @Override
      default void connectComplete(boolean reconnect, String serverURI) {
      }

      @Override
      default void authPacketArrived(int reasonCode, MqttProperties properties) {
      }
   }

   protected class LatchedMqttCallback implements DefaultMqttCallback {
      CountDownLatch latch;
      boolean fail;

      public LatchedMqttCallback(CountDownLatch latch) {
         this.latch = latch;
         this.fail = false;
      }

      public LatchedMqttCallback(CountDownLatch latch, boolean fail) {
         this.latch = latch;
         this.fail = fail;
      }

      @Override
      public void messageArrived(String topic, org.eclipse.paho.mqttv5.common.MqttMessage message) throws Exception {
         System.out.println("Message arrived: " + message);
         latch.countDown();
         if (fail) {
            throw new Exception();
         }
      }
   }
}
