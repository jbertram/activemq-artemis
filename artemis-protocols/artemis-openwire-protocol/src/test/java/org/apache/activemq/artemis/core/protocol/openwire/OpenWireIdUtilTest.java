/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.protocol.openwire;

import org.apache.activemq.artemis.utils.OpenWireIdUtil;
import org.apache.activemq.artemis.utils.RandomUtil;
import org.apache.activemq.util.IdGenerator;
import org.apache.activemq.util.LongSequenceGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OpenWireIdUtilTest {
   @Test
   public void testInvalidOpenWireIds() throws Exception {
      assertFalse(OpenWireIdUtil.isValidId("ID:foo-34685-1745263255646-3:1"));

      assertFalse(OpenWireIdUtil.isValidId(null));
      assertFalse(OpenWireIdUtil.isValidId(""));
      assertFalse(OpenWireIdUtil.isValidId("ID"));
      assertFalse(OpenWireIdUtil.isValidId("ID:"));
      assertFalse(OpenWireIdUtil.isValidId("ID:foo"));
      assertFalse(OpenWireIdUtil.isValidId("ID:foo-"));
      assertFalse(OpenWireIdUtil.isValidId("ID:foo-34685"));
      assertFalse(OpenWireIdUtil.isValidId("ID:foo-34685-"));
      assertFalse(OpenWireIdUtil.isValidId("ID:foo-34685-1745263255646"));
      assertFalse(OpenWireIdUtil.isValidId("ID:foo-34685-1745263255646-"));
      assertFalse(OpenWireIdUtil.isValidId("ID:foo-34685-1745263255646-3"));
      assertFalse(OpenWireIdUtil.isValidId("ID:foo-34685-1745263255646-3:"));
      assertFalse(OpenWireIdUtil.isValidId("ID:foo-34685-1745263255646-3:1"));
      assertFalse(OpenWireIdUtil.isValidId("ID:foo-34685-1745263255646-3:1:"));
   }

   /**
    * Test IDs generated as in {@code org.apache.activemq.command.ActiveMQTempDestination}
    *
    * @see org.apache.activemq.command.ActiveMQTempDestination#ActiveMQTempDestination(String, long)
    */
   @Test
   public void testOpenWireIdValidityWithGenerator() throws Exception {
      LongSequenceGenerator sequenceGenerator = new LongSequenceGenerator();
      for (int i = 0; i < 10000; i++) {
         String id = new IdGenerator(RandomUtil.randomAlphaNumericString(4)).generateId() + ":" + sequenceGenerator.getNextSequenceId();
         assertTrue(OpenWireIdUtil.isValidId(id), "Invalid ID: " + id);
      }
      for (int i = 0; i < 10000; i++) {
         String id = new IdGenerator().generateId() + ":" + sequenceGenerator.getNextSequenceId();
         assertTrue(OpenWireIdUtil.isValidId(id), "Invalid ID: " + id);
      }
   }

   @Test
   public void testOpenWireIdWithDashesInHostname() throws Exception {
      assertTrue(OpenWireIdUtil.isValidId("ID:hostname-with-dashes-37205-1745340475160-149:1:1"));
   }
}
