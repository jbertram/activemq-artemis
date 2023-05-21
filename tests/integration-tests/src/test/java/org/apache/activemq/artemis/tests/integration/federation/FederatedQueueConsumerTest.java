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
package org.apache.activemq.artemis.tests.integration.federation;

import org.apache.activemq.artemis.core.config.FederationConfiguration;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.federation.FederatedQueueConsumerImpl;
import org.apache.activemq.artemis.core.server.federation.Federation;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.activemq.artemis.tests.util.RandomUtil;
import org.apache.activemq.artemis.utils.Wait;
import org.junit.Test;

public class FederatedQueueConsumerTest extends ActiveMQTestBase {

   @Test
   public void testClose() throws Exception {
      ActiveMQServer server = createServer(false, createDefaultInVMConfig());
      server.start();
      Federation federation = new Federation(server, new FederationConfiguration().setName(RandomUtil.randomString()));
      federation.start();
      FederatedQueueConsumerImpl consumer = new FederatedQueueConsumerImpl(federation, server, null, null, null, null);
      consumer.start();
      Wait.waitFor(() -> consumer.getConnectionAttemptTimestamp() > 0);
      consumer.close();
      long closed = System.currentTimeMillis();
      assertFalse(Wait.waitFor(() -> consumer.getConnectionAttemptTimestamp() > closed, 5000, 100));
   }
}
