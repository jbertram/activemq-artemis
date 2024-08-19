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
package org.apache.activemq.artemis.core.protocol.core.impl.wireformat;

import org.apache.activemq.artemis.api.core.ActiveMQBuffer;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.protocol.core.impl.PacketImpl;
import org.apache.activemq.artemis.utils.DataConstants;

/**
 * Ping is sent on the client side by {@link org.apache.activemq.artemis.core.client.impl.ClientSessionFactoryImpl}. At the server's
 * side it is handled by org.apache.activemq.artemis.core.remoting.server.impl.RemotingServiceImpl
 */
public final class Ping extends PacketImpl {

   private long connectionTTL;
   private SimpleString nodeID;

   // used by clients
   public Ping(final long connectionTTL) {
      super(PING);

      this.connectionTTL = connectionTTL;
   }

   // used by the broker to respond to clients
   public Ping(final SimpleString nodeID) {
      super(PING);

      this.nodeID = nodeID;
   }

   public Ping() {
      super(PING);
   }

   public long getConnectionTTL() {
      return connectionTTL;
   }

   public SimpleString getNodeID() {
      return nodeID;
   }

   @Override
   public void encodeRest(final ActiveMQBuffer buffer) {
      buffer.writeLong(connectionTTL);
      buffer.writeNullableSimpleString(nodeID);
   }

   @Override
   public void decodeRest(final ActiveMQBuffer buffer) {
      connectionTTL = buffer.readLong();
      if (buffer.readable()) {
         nodeID = buffer.readNullableSimpleString();
      }
   }

   @Override
   public boolean isRequiresConfirmations() {
      return false;
   }

   @Override
   public int expectedEncodeSize() {
      return PACKET_HEADERS_SIZE + DataConstants.SIZE_LONG + 1 + (nodeID != null ? nodeID.sizeof() : 0);
   }

   @Override
   protected String getPacketString() {
      StringBuffer buf = new StringBuffer(super.getPacketString());
      buf.append(", connectionTTL=" + connectionTTL);
      buf.append(", nodeId=" + nodeID);
      return buf.toString();
   }

   @Override
   public int hashCode() {
      final int prime = 31;
      int result = super.hashCode();
      result = prime * result + (int) (connectionTTL ^ (connectionTTL >>> 32));
      result = prime * result + nodeID.hashCode();
      return result;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      }
      if (!super.equals(obj)) {
         return false;
      }
      if (!(obj instanceof Ping)) {
         return false;
      }
      Ping other = (Ping) obj;
      if (connectionTTL != other.connectionTTL) {
         return false;
      }
      if (nodeID == null) {
         if (other.nodeID != null) {
            return false;
         }
      } else if (!nodeID.equals(other.nodeID)) {
         return false;
      }
      return true;
   }
}
