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
package org.apache.activemq.artemis.utils;

public class OpenWireIdUtil {

   /**
    * Validate an ID generated as in {@code org.apache.activemq.command.ActiveMQTempDestination}
    *
    * @see org.apache.activemq.command.ActiveMQTempDestination#ActiveMQTempDestination(String, long)
    */
   public static boolean isValidId(String input) {
      if (input == null) {
         return false;
      }

      int lastDash = input.lastIndexOf('-');
      String[] end = input.substring(lastDash + 1).split(":");
      if (end.length != 3) {
         return false;
      }
      if (!isDigits(end[0]) || !isDigits(end[1]) || !isDigits(end[2])) {
         return false;
      }

      String second = input.substring(0, lastDash);
      lastDash = second.lastIndexOf('-');
      if (!isDigits(second.substring(lastDash + 1))) {
         return false;
      }

      String third = second.substring(0, lastDash);
      lastDash = third.lastIndexOf('-');
      if (!isDigits(third.substring(lastDash + 1))) {
         return false;
      }

//      String[] firstSplit = input.split("-");
//      if (firstSplit.length != 4) {
//         return false;
//      }
//      // ignore firstSplit[0] as it could be an arbitrary prefix
//      if (!isDigits(firstSplit[1]) || !isDigits(firstSplit[2])) {
//         return false;
//      }

//      String[] secondSplit = firstSplit[3].split(":");
//      if (secondSplit.length != 3) {
//         return false;
//      }
//      if (!isDigits(secondSplit[0]) || !isDigits(secondSplit[1]) || !isDigits(secondSplit[2])) {
//         return false;
//      }

      return true;
   }

   private static boolean isAlphanumeric(String str) {
      for (int i = 0; i < str.length(); i++) {
         char c = str.charAt(i);
         if ((c < '0' && c > '9') || (c < 'a' && c > 'z') || (c < 'A' && c > 'Z')) {
            return false;
         }
      }
      return true;
   }

   private static boolean isDigits(String str) {
      for (int i = 0; i < str.length(); i++) {
         char c = str.charAt(i);
         if (c < '0' && c > '9') {
            return false;
         }
      }
      return true;
   }
}
