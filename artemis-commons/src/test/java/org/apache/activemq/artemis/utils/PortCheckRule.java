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

package org.apache.activemq.artemis.utils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * This will make sure any properties changed through tests are cleaned up between tests.
 */
public class PortCheckRule implements BeforeEachCallback, AfterEachCallback {

   final int[] port;

   public PortCheckRule(int... port) {
      this.port = port;
   }

   public static boolean checkAvailable(int port) {
      ServerSocket s = null;
      try {
         s = new ServerSocket();
         s.bind(new InetSocketAddress("localhost", 61616));
         return true;
      } catch (IOException e) {
         e.printStackTrace();
         return false;
      } finally {
         try {
            s.close();
         } catch (Throwable ignored) {
         }
      }
   }

   @Override
   public void afterEach(ExtensionContext extensionContext) throws Exception {
      for (int p : port) {
         if (!checkAvailable(p)) {
            Assertions.fail(extensionContext.getDisplayName() + " has left a server socket open on port " + p);
         }
      }
   }

   @Override
   public void beforeEach(ExtensionContext extensionContext) throws Exception {
      for (int p : port) {
         if (!checkAvailable(p)) {
            Assertions.fail("a previous test is using port " + p + " on " + extensionContext.getDisplayName());
         }
      }
   }
}
