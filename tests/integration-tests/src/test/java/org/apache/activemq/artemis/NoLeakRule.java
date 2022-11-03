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
import org.junit.Assert;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * This is useful to make sure you won't have leaking threads between tests
 */
public class NoLeakRule extends TestWatcher {

   boolean before;
   boolean after;

   public NoLeakRule(String className, boolean before, boolean after, int expectedInstances, int reportDepth) {
      this.className = className;
      this.expectedInstances = expectedInstances;
      this.reportDepth = reportDepth;
      this.before = before;
      this.after = after;

      Assert.assertTrue("You have to choose between before, after or both", before || after);
   }

   String className;
   int expectedInstances;
   int reportDepth;

   @Override
   protected void starting(Description description) {
      super.starting(description);

      if (before) {

         try {
            JVMTIInterface.noLeaks(className, expectedInstances, reportDepth);
         } catch (Exception e) {
            e.printStackTrace(); // this may go out to junit report
            Assert.fail("Test " + description.getClassName() + " is starting while previous tests had leaks -- " + e.getMessage());
         }
      }

   }

   /**
    * Override to tear down your specific external resource.
    */
   @Override
   protected void finished(Description description) {

      if (after) {
         try {
            JVMTIInterface.noLeaks(className, expectedInstances, reportDepth);
         } catch (Exception e) {
            e.printStackTrace(); // this may go out to junit report
            Assert.fail("Test " + description.getClassName() + " is starting while previous tests had leaks -- " + e.getMessage());
         }
      }

   }

}
