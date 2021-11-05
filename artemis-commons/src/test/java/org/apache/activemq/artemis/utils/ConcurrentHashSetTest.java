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

import java.util.Iterator;

import org.apache.activemq.artemis.utils.collections.ConcurrentHashSet;
import org.apache.activemq.artemis.utils.collections.ConcurrentSet;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ConcurrentHashSetTest extends Assertions {


   private ConcurrentSet<String> set;

   private String element;


   @Test
   public void testAdd() throws Exception {
      Assertions.assertTrue(set.add(element));
      Assertions.assertFalse(set.add(element));
   }

   @Test
   public void testAddIfAbsent() throws Exception {
      Assertions.assertTrue(set.addIfAbsent(element));
      Assertions.assertFalse(set.addIfAbsent(element));
   }

   @Test
   public void testRemove() throws Exception {
      Assertions.assertTrue(set.add(element));

      Assertions.assertTrue(set.remove(element));
      Assertions.assertFalse(set.remove(element));
   }

   @Test
   public void testContains() throws Exception {
      Assertions.assertFalse(set.contains(element));

      Assertions.assertTrue(set.add(element));
      Assertions.assertTrue(set.contains(element));

      Assertions.assertTrue(set.remove(element));
      Assertions.assertFalse(set.contains(element));
   }

   @Test
   public void testSize() throws Exception {
      Assertions.assertEquals(0, set.size());

      Assertions.assertTrue(set.add(element));
      Assertions.assertEquals(1, set.size());

      Assertions.assertTrue(set.remove(element));
      Assertions.assertEquals(0, set.size());
   }

   @Test
   public void testClear() throws Exception {
      Assertions.assertTrue(set.add(element));

      Assertions.assertTrue(set.contains(element));
      set.clear();
      Assertions.assertFalse(set.contains(element));
   }

   @Test
   public void testIsEmpty() throws Exception {
      Assertions.assertTrue(set.isEmpty());

      Assertions.assertTrue(set.add(element));
      Assertions.assertFalse(set.isEmpty());

      set.clear();
      Assertions.assertTrue(set.isEmpty());
   }

   @Test
   public void testIterator() throws Exception {
      set.add(element);

      Iterator<String> iterator = set.iterator();
      while (iterator.hasNext()) {
         String e = iterator.next();
         Assertions.assertEquals(element, e);
      }
   }

   @BeforeEach
   public void setUp() throws Exception {
      set = new ConcurrentHashSet<>();
      element = RandomUtil.randomString();
   }
}
