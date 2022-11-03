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

package org.apache.activemq.artemis.tests.integration.amqp;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;

import com.dsect.jvmti.JVMTIInterface;
import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.tests.util.ActiveMQTestBase;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.Before;
import org.junit.Test;

public class MyTest extends ActiveMQTestBase {

   ActiveMQServer server;

   @Override
   @Before
   public void setUp() throws Exception {

      server = createServer(true, createDefaultConfig(1, true));
      server.start();
   }

   @Test
   public void simpleTest() throws Exception {
      JVMTIInterface jvmtiInterface = new JVMTIInterface();
      jvmtiInterface.forceGC();

      Object[] objects = jvmtiInterface.getAllObjects("org.apache.activemq.artemis.core.server.impl.RoutingContextImpl");
      System.out.println("There are " + objects.length + " references");

      ConnectionFactory cf = new JmsConnectionFactory("amqp://localhost:61616");

      try (Connection producerConnection = cf.createConnection();
           Connection consumerConnection = cf.createConnection()) {

         Session producerSession = producerConnection.createSession();

         Session consumerSession = consumerConnection.createSession(Session.SESSION_TRANSACTED);
         consumerConnection.start();

         for (int i = 0; i < 10; i++) {
            {
               Destination source = producerSession.createQueue("source");
               MessageProducer sourceProducer = producerSession.createProducer(source);
               sourceProducer.send(producerSession.createMessage());
               sourceProducer.close();
            }
            {
               Destination source = consumerSession.createQueue("source");
               Destination target = consumerSession.createQueue("target");
               MessageConsumer sourceConsumer = consumerSession.createConsumer(source);
               MessageProducer targetProducer = consumerSession.createProducer(target);
               Message m = sourceConsumer.receive();
               targetProducer.send(m);
               consumerSession.commit();
               sourceConsumer.close();
               //targetProducer.close(); // this line fixes the leak on the broker
            }
         }

      }

      Thread.sleep(1500);

      //report("org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerReceiverContext", 30, 4);
      //report("org.apache.activemq.artemis.protocol.amqp.proton.ProtonServerSenderContext", 30, 4);
      //report("org.apache.activemq.artemis.core.server.impl.ServerConsumerImpl", 15, 5);
      report("org.apache.activemq.artemis.core.server.impl.RoutingContextImpl", 15, 5);
      //report("org.apache.activemq.artemis.protocol.amqp.broker.AMQPSessionCallback", 15, 5);
   }

   private void report(String clazz, int levels, int maxObjects) throws Exception {
      JVMTIInterface jvmtiInterface = new JVMTIInterface();
      Object[] objects;
      jvmtiInterface.forceGC();
      objects = jvmtiInterface.getAllObjects(clazz);
      System.out.println("*******************************************************************************************************************************");
      System.out.println("There are " + objects.length + " references of " + clazz);
      System.out.println("*******************************************************************************************************************************");

      for (Object obj : objects) {
         System.out.println("Refs: " + jvmtiInterface.exploreObjectReferences(levels, true, obj));
         if (--maxObjects <= 0) {
            return;
         }
      }
   }

}