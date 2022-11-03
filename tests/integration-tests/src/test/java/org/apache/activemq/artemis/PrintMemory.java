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

package org.apache.activemq.artemis;

import com.dsect.jvmti.JVMTIInterface;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * This is useful to make sure you won't have leaking threads between tests
 */
public class PrintMemory extends TestWatcher {

   public PrintMemory() {
   }

   private void report(Description description) {
      try {
         JVMTIInterface jvmtiInterface = new JVMTIInterface();
         System.out.println("Memory report after " + description);
         System.out.println(jvmtiInterface.inventoryReport(false));
      } catch (Exception e) {
         e.printStackTrace(); // this may go out to junit report
      }
   }

   /**
    * Override to tear down your specific external resource.
    */
   @Override
   protected void finished(Description description) {

      report(description);

   }

}
