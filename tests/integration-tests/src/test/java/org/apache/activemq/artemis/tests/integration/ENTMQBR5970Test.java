/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <br>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <br>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.activemq.artemis.tests.integration;

import javax.security.auth.Subject;
import javax.transaction.xa.Xid;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

import io.netty.channel.ChannelFutureListener;
import org.apache.activemq.artemis.Closeable;
import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.Pair;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.persistence.OperationContext;
import org.apache.activemq.artemis.core.postoffice.RoutingStatus;
import org.apache.activemq.artemis.core.remoting.CloseListener;
import org.apache.activemq.artemis.core.remoting.FailureListener;
import org.apache.activemq.artemis.core.security.ActiveMQPrincipal;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.AddressQueryResult;
import org.apache.activemq.artemis.core.server.BindingQueryResult;
import org.apache.activemq.artemis.core.server.MessageReference;
import org.apache.activemq.artemis.core.server.Queue;
import org.apache.activemq.artemis.core.server.QueueQueryResult;
import org.apache.activemq.artemis.core.server.RoutingContext;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerProducer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.impl.ActiveMQServerImpl;
import org.apache.activemq.artemis.core.server.impl.AddressInfo;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.json.JsonArrayBuilder;
import org.apache.activemq.artemis.spi.core.protocol.ConnectionEntry;
import org.apache.activemq.artemis.spi.core.protocol.RemotingConnection;
import org.apache.activemq.artemis.spi.core.remoting.ReadyListener;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.RandomUtil;
import org.junit.Before;
import org.junit.Test;

public class ENTMQBR5970Test extends ActiveMQTestBase {

   protected ActiveMQServer server;

   @Override
   @Before
   public void setUp() throws Exception {
      org.jboss.logmanager.Logger.getLogger("ENTMQBR-5970").setLevel(org.jboss.logmanager.Level.TRACE);
      super.setUp();
      server = createServer(false, createDefaultNettyConfig());
      server.start();
   }

   @Test
   public void loggingTest() throws Exception {
      String connectionID = RandomUtil.randomString();
      ((ActiveMQServerImpl)server).addSession(new MySession(connectionID));
      ((ActiveMQServerImpl)server).addSession(new MySession("foo"));
      ((ActiveMQServerImpl)server).addSession(new MySession("bar"));
      MyConnection connection = new MyConnection(connectionID);
      server.getRemotingService().addConnectionEntry(connection, new ConnectionEntry(new MyRemotingConnection(connectionID), null, 0, 0));
      server.getActiveMQServerControl().logOrphanedSessions();
   }

   @Test
   public void removalTest() throws Exception {
      String connectionID = RandomUtil.randomString();
      ((ActiveMQServerImpl)server).addSession(new MySession(connectionID));
      ((ActiveMQServerImpl)server).addSession(new MySession("foo"));
      MyConnection connection = new MyConnection(connectionID);
      server.getRemotingService().addConnectionEntry(connection, new ConnectionEntry(new MyRemotingConnection(connectionID), null, 0, 0));
      assertEquals(2, server.getSessions().size());
      server.getActiveMQServerControl().closeOrphanedSessions();
      assertEquals(1, server.getSessions().size());
   }

   class MyRemotingConnection implements RemotingConnection {

      String connectionID;

      MyRemotingConnection(String connectionID) {
         this.connectionID = connectionID;
      }

      @Override
      public Object getID() {
         return connectionID;
      }

      @Override
      public long getCreationTime() {
         return 0;
      }

      @Override
      public String getRemoteAddress() {
         return null;
      }

      @Override
      public void scheduledFlush() {

      }

      @Override
      public void addFailureListener(FailureListener listener) {

      }

      @Override
      public boolean removeFailureListener(FailureListener listener) {
         return false;
      }

      @Override
      public void addCloseListener(CloseListener listener) {

      }

      @Override
      public boolean removeCloseListener(CloseListener listener) {
         return false;
      }

      @Override
      public List<CloseListener> removeCloseListeners() {
         return null;
      }

      @Override
      public void setCloseListeners(List<CloseListener> listeners) {

      }

      @Override
      public List<FailureListener> getFailureListeners() {
         return null;
      }

      @Override
      public List<FailureListener> removeFailureListeners() {
         return null;
      }

      @Override
      public void setFailureListeners(List<FailureListener> listeners) {

      }

      @Override
      public ActiveMQBuffer createTransportBuffer(int size) {
         return null;
      }

      @Override
      public void fail(ActiveMQException me) {

      }

      @Override
      public Future asyncFail(ActiveMQException me) {
         return null;
      }

      @Override
      public void fail(ActiveMQException me, String scaleDownTargetNodeID) {

      }

      @Override
      public void destroy() {

      }

      @Override
      public org.apache.activemq.artemis.spi.core.remoting.Connection getTransportConnection() {
         return null;
      }

      @Override
      public boolean isClient() {
         return false;
      }

      @Override
      public boolean isDestroyed() {
         return false;
      }

      @Override
      public void disconnect(boolean criticalError) {

      }

      @Override
      public void disconnect(String scaleDownNodeID, boolean criticalError) {

      }

      @Override
      public boolean checkDataReceived() {
         return false;
      }

      @Override
      public void flush() {

      }

      @Override
      public boolean isWritable(ReadyListener callback) {
         return false;
      }

      @Override
      public void killMessage(SimpleString nodeID) {

      }

      @Override
      public boolean isSupportReconnect() {
         return false;
      }

      @Override
      public boolean isSupportsFlowControl() {
         return false;
      }

      @Override
      public void setAuditSubject(Subject subject) {

      }

      @Override
      public Subject getAuditSubject() {
         return null;
      }

      @Override
      public Subject getSubject() {
         return null;
      }

      @Override
      public String getProtocolName() {
         return null;
      }

      @Override
      public void setClientID(String cID) {

      }

      @Override
      public String getClientID() {
         return null;
      }

      @Override
      public String getTransportLocalAddress() {
         return null;
      }

      @Override
      public void bufferReceived(Object connectionID, ActiveMQBuffer buffer) {

      }
   }

   class MyConnection implements org.apache.activemq.artemis.spi.core.remoting.Connection  {

      String connectionID;

      MyConnection(String connectionID) {
         this.connectionID = connectionID;
      }

      @Override
      public ActiveMQBuffer createTransportBuffer(int size) {
         return null;
      }

      @Override
      public RemotingConnection getProtocolConnection() {
         return null;
      }

      @Override
      public void setProtocolConnection(RemotingConnection connection) {

      }

      @Override
      public boolean isWritable(ReadyListener listener) {
         return false;
      }

      @Override
      public boolean isOpen() {
         return false;
      }

      @Override
      public void fireReady(boolean ready) {

      }

      @Override
      public void setAutoRead(boolean autoRead) {

      }

      @Override
      public Object getID() {
         return connectionID;
      }

      @Override
      public void write(ActiveMQBuffer buffer, boolean requestFlush) {

      }

      @Override
      public void write(ActiveMQBuffer buffer, boolean flush, boolean batched) {

      }

      @Override
      public void write(ActiveMQBuffer buffer, boolean flush, boolean batched, ChannelFutureListener futureListener) {

      }

      @Override
      public void write(ActiveMQBuffer buffer) {

      }

      @Override
      public void forceClose() {

      }

      @Override
      public void close() {

      }

      @Override
      public String getRemoteAddress() {
         return null;
      }

      @Override
      public String getLocalAddress() {
         return null;
      }

      @Override
      public void checkFlushBatchBuffer() {

      }

      @Override
      public TransportConfiguration getConnectorConfig() {
         return null;
      }

      @Override
      public boolean isDirectDeliver() {
         return false;
      }

      @Override
      public ActiveMQPrincipal getDefaultActiveMQPrincipal() {
         return null;
      }

      @Override
      public boolean isUsingProtocolHandling() {
         return false;
      }

      @Override
      public boolean isSameTarget(TransportConfiguration... configs) {
         return false;
      }
   }

   class MySession implements ServerSession {

      String name = RandomUtil.randomString();
      String connectionID;

      MySession(String connectionID) {
         this.connectionID = connectionID;
      }


      @Override
      public String toString() {
         StringBuilder sb = new StringBuilder();
         sb.append("ServerSessionImpl[");
         sb.append("name=").append(name).append(", ");
         sb.append("connectionID=").append(getConnectionID());
         sb.append("]@").append(Integer.toHexString(System.identityHashCode(this)));
         return sb.toString();
      }

      @Override
      public String getName() {
         return name;
      }

      @Override
      public int getMinLargeMessageSize() {
         return 0;
      }

      @Override
      public Object getConnectionID() {
         return connectionID;
      }

      @Override
      public Executor getSessionExecutor() {
         return null;
      }

      @Override
      public void enableSecurity() {

      }

      @Override
      public void disableSecurity() {

      }

      @Override
      public RemotingConnection getRemotingConnection() {
         return null;
      }

      @Override
      public void transferConnection(RemotingConnection newConnection) {

      }

      @Override
      public Transaction newTransaction() {
         return null;
      }

      @Override
      public boolean removeConsumer(long consumerID) throws Exception {
         return false;
      }

      @Override
      public List<Long> acknowledge(long consumerID, long messageID) throws Exception {
         return null;
      }

      @Override
      public void individualAcknowledge(long consumerID, long messageID) throws Exception {

      }

      @Override
      public void individualCancel(long consumerID, long messageID, boolean failed) throws Exception {

      }

      @Override
      public void expire(long consumerID, long messageID) throws Exception {

      }

      @Override
      public void rollback(boolean considerLastMessageAsDelivered) throws Exception {

      }

      @Override
      public void commit() throws Exception {

      }

      @Override
      public void xaCommit(Xid xid, boolean onePhase) throws Exception {

      }

      @Override
      public void xaEnd(Xid xid) throws Exception {

      }

      @Override
      public void xaForget(Xid xid) throws Exception {

      }

      @Override
      public void xaJoin(Xid xid) throws Exception {

      }

      @Override
      public void xaPrepare(Xid xid) throws Exception {

      }

      @Override
      public void xaResume(Xid xid) throws Exception {

      }

      @Override
      public void xaRollback(Xid xid) throws Exception {

      }

      @Override
      public void xaStart(Xid xid) throws Exception {

      }

      @Override
      public void xaFailed(Xid xid) throws Exception {

      }

      @Override
      public void xaSuspend() throws Exception {

      }

      @Override
      public void markTXFailed(Throwable e) {

      }

      @Override
      public List<Xid> xaGetInDoubtXids() {
         return null;
      }

      @Override
      public int xaGetTimeout() {
         return 0;
      }

      @Override
      public void xaSetTimeout(int timeout) {

      }

      @Override
      public void start() {

      }

      @Override
      public void stop() {

      }

      @Override
      public void addCloseable(Closeable closeable) {

      }

      @Override
      public ServerConsumer createConsumer(long consumerID,
                                           SimpleString queueName,
                                           SimpleString filterString,
                                           int priority,
                                           boolean browseOnly,
                                           boolean supportLargeMessage,
                                           Integer credits) throws Exception {
         return null;
      }

      @Override
      public void resetTX(Transaction transaction) {

      }

      @Override
      public Queue createQueue(SimpleString address,
                               SimpleString name,
                               RoutingType routingType,
                               SimpleString filterString,
                               boolean temporary,
                               boolean durable) throws Exception {
         return null;
      }

      @Override
      public Queue createQueue(AddressInfo address,
                               SimpleString name,
                               SimpleString filterString,
                               boolean temporary,
                               boolean durable) throws Exception {
         return null;
      }

      @Override
      public Queue createQueue(SimpleString address,
                               SimpleString name,
                               SimpleString filterString,
                               boolean temporary,
                               boolean durable) throws Exception {
         return null;
      }

      @Override
      public Queue createQueue(SimpleString address,
                               SimpleString name,
                               RoutingType routingType,
                               SimpleString filterString,
                               boolean temporary,
                               boolean durable,
                               int maxConsumers,
                               boolean purgeOnNoConsumers,
                               boolean autoCreated) throws Exception {
         return null;
      }

      @Override
      public Queue createQueue(SimpleString address,
                               SimpleString name,
                               RoutingType routingType,
                               SimpleString filterString,
                               boolean temporary,
                               boolean durable,
                               int maxConsumers,
                               boolean purgeOnNoConsumers,
                               Boolean exclusive,
                               Boolean lastValue,
                               boolean autoCreated) throws Exception {
         return null;
      }

      @Override
      public Queue createQueue(SimpleString address,
                               SimpleString name,
                               RoutingType routingType,
                               SimpleString filterString,
                               boolean temporary,
                               boolean durable,
                               int maxConsumers,
                               boolean purgeOnNoConsumers,
                               Boolean exclusive,
                               Boolean groupRebalance,
                               Integer groupBuckets,
                               Boolean lastValue,
                               SimpleString lastValueKey,
                               Boolean nonDestructive,
                               Integer consumersBeforeDispatch,
                               Long delayBeforeDispatch,
                               Boolean autoDelete,
                               Long autoDeleteDelay,
                               Long autoDeleteMessageCount,
                               boolean autoCreated) throws Exception {
         return null;
      }

      @Override
      public Queue createQueue(SimpleString address,
                               SimpleString name,
                               RoutingType routingType,
                               SimpleString filterString,
                               boolean temporary,
                               boolean durable,
                               int maxConsumers,
                               boolean purgeOnNoConsumers,
                               Boolean exclusive,
                               Boolean groupRebalance,
                               Integer groupBuckets,
                               SimpleString groupFirstKey,
                               Boolean lastValue,
                               SimpleString lastValueKey,
                               Boolean nonDestructive,
                               Integer consumersBeforeDispatch,
                               Long delayBeforeDispatch,
                               Boolean autoDelete,
                               Long autoDeleteDelay,
                               Long autoDeleteMessageCount,
                               boolean autoCreated) throws Exception {
         return null;
      }

      @Override
      public Queue createQueue(SimpleString address,
                               SimpleString name,
                               RoutingType routingType,
                               SimpleString filterString,
                               boolean temporary,
                               boolean durable,
                               int maxConsumers,
                               boolean purgeOnNoConsumers,
                               Boolean exclusive,
                               Boolean groupRebalance,
                               Integer groupBuckets,
                               SimpleString groupFirstKey,
                               Boolean lastValue,
                               SimpleString lastValueKey,
                               Boolean nonDestructive,
                               Integer consumersBeforeDispatch,
                               Long delayBeforeDispatch,
                               Boolean autoDelete,
                               Long autoDeleteDelay,
                               Long autoDeleteMessageCount,
                               boolean autoCreated,
                               Long ringSize) throws Exception {
         return null;
      }

      @Override
      public Queue createQueue(SimpleString address,
                               SimpleString name,
                               RoutingType routingType,
                               SimpleString filterString,
                               boolean temporary,
                               boolean durable,
                               boolean autoCreated) throws Exception {
         return null;
      }

      @Override
      public Queue createQueue(AddressInfo addressInfo,
                               SimpleString name,
                               SimpleString filterString,
                               boolean temporary,
                               boolean durable,
                               boolean autoCreated) throws Exception {
         return null;
      }

      @Override
      public Queue createQueue(AddressInfo addressInfo,
                               SimpleString name,
                               SimpleString filterString,
                               boolean temporary,
                               boolean durable,
                               Boolean exclusive,
                               Boolean lastValue,
                               boolean autoCreated) throws Exception {
         return null;
      }

      @Override
      public Queue createQueue(QueueConfiguration queueConfiguration) throws Exception {
         return null;
      }

      @Override
      public AddressInfo createAddress(SimpleString address,
                                       EnumSet<RoutingType> routingTypes,
                                       boolean autoCreated) throws Exception {
         return null;
      }

      @Override
      public AddressInfo createAddress(SimpleString address,
                                       RoutingType routingType,
                                       boolean autoCreated) throws Exception {
         return null;
      }

      @Override
      public AddressInfo createAddress(AddressInfo addressInfo, boolean autoCreated) throws Exception {
         return null;
      }

      @Override
      public void deleteQueue(SimpleString name) throws Exception {

      }

      @Override
      public ServerConsumer createConsumer(long consumerID,
                                           SimpleString queueName,
                                           SimpleString filterString,
                                           boolean browseOnly) throws Exception {
         return null;
      }

      @Override
      public ServerConsumer createConsumer(long consumerID,
                                           SimpleString queueName,
                                           SimpleString filterString,
                                           boolean browseOnly,
                                           boolean supportLargeMessage,
                                           Integer credits) throws Exception {
         return null;
      }

      @Override
      public QueueQueryResult executeQueueQuery(SimpleString name) throws Exception {
         return null;
      }

      @Override
      public AddressQueryResult executeAddressQuery(SimpleString name) throws Exception {
         return null;
      }

      @Override
      public BindingQueryResult executeBindingQuery(SimpleString address) throws Exception {
         return null;
      }

      @Override
      public void closeConsumer(long consumerID) throws Exception {

      }

      @Override
      public void receiveConsumerCredits(long consumerID, int credits) throws Exception {

      }

      @Override
      public RoutingStatus send(Transaction tx,
                                Message message,
                                boolean direct,
                                boolean noAutoCreateQueue) throws Exception {
         return null;
      }

      @Override
      public RoutingStatus send(Transaction tx,
                                Message message,
                                boolean direct,
                                boolean noAutoCreateQueue,
                                RoutingContext routingContext) throws Exception {
         return null;
      }

      @Override
      public RoutingStatus doSend(Transaction tx,
                                  Message msg,
                                  SimpleString originalAddress,
                                  boolean direct,
                                  boolean noAutoCreateQueue) throws Exception {
         return null;
      }

      @Override
      public RoutingStatus doSend(Transaction tx,
                                  Message msg,
                                  SimpleString originalAddress,
                                  boolean direct,
                                  boolean noAutoCreateQueue,
                                  RoutingContext routingContext) throws Exception {
         return null;
      }

      @Override
      public RoutingStatus send(Message message, boolean direct, boolean noAutoCreateQueue) throws Exception {
         return null;
      }

      @Override
      public RoutingStatus send(Message message, boolean direct) throws Exception {
         return null;
      }

      @Override
      public void forceConsumerDelivery(long consumerID, long sequence) throws Exception {

      }

      @Override
      public void requestProducerCredits(SimpleString address, int credits) throws Exception {

      }

      @Override
      public void close(boolean failed) throws Exception {
         server.removeSession(this.name);
      }

      @Override
      public void setTransferring(boolean transferring) {

      }

      @Override
      public Set<ServerConsumer> getServerConsumers() {
         return null;
      }

      @Override
      public void addMetaData(String key, String data) throws Exception {

      }

      @Override
      public boolean addUniqueMetaData(String key, String data) throws Exception {
         return false;
      }

      @Override
      public String getMetaData(String key) {
         return null;
      }

      @Override
      public Map<String, String> getMetaData() {
         return null;
      }

      @Override
      public String[] getTargetAddresses() {
         return new String[0];
      }

      @Override
      public void describeProducersInfo(JsonArrayBuilder objs) throws Exception {

      }

      @Override
      public String getLastSentMessageID(String address) {
         return null;
      }

      @Override
      public long getCreationTime() {
         return 0;
      }

      @Override
      public OperationContext getSessionContext() {
         return null;
      }

      @Override
      public Transaction getCurrentTransaction() {
         return null;
      }

      @Override
      public ServerConsumer locateConsumer(long consumerID) throws Exception {
         return null;
      }

      @Override
      public boolean isClosed() {
         return false;
      }

      @Override
      public void createSharedQueue(SimpleString address,
                                    SimpleString name,
                                    RoutingType routingType,
                                    SimpleString filterString,
                                    boolean durable,
                                    Integer maxConsumers,
                                    Boolean purgeOnNoConsumers,
                                    Boolean exclusive,
                                    Boolean lastValue) throws Exception {

      }

      @Override
      public void createSharedQueue(SimpleString address,
                                    SimpleString name,
                                    RoutingType routingType,
                                    SimpleString filterString,
                                    boolean durable,
                                    Integer maxConsumers,
                                    Boolean purgeOnNoConsumers,
                                    Boolean exclusive,
                                    Boolean groupRebalance,
                                    Integer groupBuckets,
                                    Boolean lastValue,
                                    SimpleString lastValueKey,
                                    Boolean nonDestructive,
                                    Integer consumersBeforeDispatch,
                                    Long delayBeforeDispatch,
                                    Boolean autoDelete,
                                    Long autoDeleteDelay,
                                    Long autoDeleteMessageCount) throws Exception {

      }

      @Override
      public void createSharedQueue(SimpleString address,
                                    SimpleString name,
                                    RoutingType routingType,
                                    SimpleString filterString,
                                    boolean durable,
                                    Integer maxConsumers,
                                    Boolean purgeOnNoConsumers,
                                    Boolean exclusive,
                                    Boolean groupRebalance,
                                    Integer groupBuckets,
                                    SimpleString groupFirstKey,
                                    Boolean lastValue,
                                    SimpleString lastValueKey,
                                    Boolean nonDestructive,
                                    Integer consumersBeforeDispatch,
                                    Long delayBeforeDispatch,
                                    Boolean autoDelete,
                                    Long autoDeleteDelay,
                                    Long autoDeleteMessageCount) throws Exception {

      }

      @Override
      public void createSharedQueue(SimpleString address,
                                    SimpleString name,
                                    RoutingType routingType,
                                    boolean durable,
                                    SimpleString filterString) throws Exception {

      }

      @Override
      public void createSharedQueue(SimpleString address,
                                    SimpleString name,
                                    boolean durable,
                                    SimpleString filterString) throws Exception {

      }

      @Override
      public void createSharedQueue(QueueConfiguration queueConfiguration) throws Exception {

      }

      @Override
      public List<MessageReference> getInTXMessagesForConsumer(long consumerId) {
         return null;
      }

      @Override
      public List<MessageReference> getInTxLingerMessages() {
         return null;
      }

      @Override
      public void addLingerConsumer(ServerConsumer consumer) {

      }

      @Override
      public String getValidatedUser() {
         return null;
      }

      @Override
      public SimpleString getMatchingQueue(SimpleString address, RoutingType routingType) throws Exception {
         return null;
      }

      @Override
      public SimpleString getMatchingQueue(SimpleString address,
                                           SimpleString queueName,
                                           RoutingType routingType) throws Exception {
         return null;
      }

      @Override
      public AddressInfo getAddress(SimpleString address) {
         return null;
      }

      @Override
      public SimpleString removePrefix(SimpleString address) {
         return null;
      }

      @Override
      public SimpleString getPrefix(SimpleString address) {
         return null;
      }

      @Override
      public AddressInfo getAddressAndRoutingType(AddressInfo addressInfo) {
         return null;
      }

      @Override
      public RoutingType getRoutingTypeFromPrefix(SimpleString address, RoutingType defaultRoutingType) {
         return null;
      }

      @Override
      public Pair<SimpleString, EnumSet<RoutingType>> getAddressAndRoutingTypes(SimpleString address,
                                                                                EnumSet<RoutingType> defaultRoutingTypes) {
         return null;
      }

      @Override
      public void addProducer(ServerProducer serverProducer) {

      }

      @Override
      public void removeProducer(String ID) {

      }

      @Override
      public Map<String, ServerProducer> getServerProducers() {
         return null;
      }

      @Override
      public String getDefaultAddress() {
         return null;
      }

      @Override
      public int getConsumerCount() {
         return 0;
      }

      @Override
      public int getProducerCount() {
         return 0;
      }

      @Override
      public int getDefaultConsumerWindowSize(SimpleString address) {
         return 0;
      }

      @Override
      public String toManagementString() {
         return null;
      }

      @Override
      public String getUsername() {
         return null;
      }

      @Override
      public String getPassword() {
         return null;
      }

      @Override
      public String getSecurityDomain() {
         return null;
      }
   }
}
