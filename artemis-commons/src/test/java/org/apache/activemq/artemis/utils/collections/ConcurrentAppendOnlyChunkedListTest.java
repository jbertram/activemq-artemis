/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */
package org.apache.activemq.artemis.utils.collections;

import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ConcurrentAppendOnlyChunkedListTest {

   private static final int CHUNK_SIZE = 16;
   private static final int ELEMENTS = (CHUNK_SIZE * 4) + 1;

   private final ConcurrentAppendOnlyChunkedList<Integer> chunkedList;

   public ConcurrentAppendOnlyChunkedListTest() {
      chunkedList = new ConcurrentAppendOnlyChunkedList<>(CHUNK_SIZE);
   }

	@Test
   public void shouldFailToCreateNotPowerOf2ChunkSizeCollection() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
         new ConcurrentAppendOnlyChunkedList<>(3);
      });
   }

	@Test
   public void shouldFailToCreateNegativeChunkSizeCollection() {
		Assertions.assertThrows(IllegalArgumentException.class, () -> {
         new ConcurrentAppendOnlyChunkedList<>(-1);
      });
   }

   @Test
   public void shouldNumberOfElementsBeTheSameOfTheAddedElements() {
      final int messages = ELEMENTS;
      for (int i = 0; i < messages; i++) {
         Assertions.assertEquals(i, chunkedList.size());
         chunkedList.add(i);
      }
      Assertions.assertEquals(messages, chunkedList.size());
   }

   @Test
   public void shouldNumberOfElementsBeTheSameOfAddAllElements() {
      final int messages = ELEMENTS;
      final Integer[] elements = new Integer[messages];
      for (int i = 0; i < messages; i++) {
         final Integer element = i;
         elements[i] = element;
      }
      chunkedList.addAll(elements);
      Assertions.assertEquals(messages, chunkedList.size());
   }

   @Test
   public void shouldGetReturnNullIfEmpty() {
      Assertions.assertNull(chunkedList.get(0));
   }

   @Test
   public void shouldNegativeIndexedGetReturnNull() {
      Assertions.assertNull(chunkedList.get(-1));
      chunkedList.add(0);
      Assertions.assertNull(chunkedList.get(-1));
   }

   @Test
   public void shouldGetReturnNullIfExceedSize() {
      final int messages = ELEMENTS;
      for (int i = 0; i < messages; i++) {
         final Integer element = i;
         chunkedList.add(element);
         Assertions.assertNull(chunkedList.get(i + 1));
      }
   }

   @Test
   public void shouldGetReturnElementsAccordingToAddOrder() {
      final int messages = ELEMENTS;
      final Integer[] elements = new Integer[messages];
      for (int i = 0; i < messages; i++) {
         final Integer element = i;
         elements[i] = element;
         chunkedList.add(element);
      }
      final Integer[] cachedElements = new Integer[messages];
      for (int i = 0; i < messages; i++) {
         cachedElements[i] = chunkedList.get(i);
      }
      Assertions.assertArrayEquals(elements, cachedElements);
      Arrays.fill(cachedElements, null);
      for (int i = messages - 1; i >= 0; i--) {
         cachedElements[i] = chunkedList.get(i);
      }
      Assertions.assertArrayEquals(elements, cachedElements);
   }

   @Test
   public void shouldGetReturnElementsAccordingToAddAllOrder() {
      final int messages = ELEMENTS;
      final Integer[] elements = new Integer[messages];
      for (int i = 0; i < messages; i++) {
         final Integer element = i;
         elements[i] = element;
      }
      chunkedList.addAll(elements);
      final Integer[] cachedElements = new Integer[messages];
      for (int i = 0; i < messages; i++) {
         cachedElements[i] = chunkedList.get(i);
      }
      Assertions.assertArrayEquals(elements, cachedElements);
   }

   @Test
   public void shouldToArrayReturnElementsAccordingToAddOrder() {
      final int messages = ELEMENTS;
      final Integer[] elements = new Integer[messages];
      for (int i = 0; i < messages; i++) {
         final Integer element = i;
         elements[i] = element;
         chunkedList.add(element);
      }
      final Integer[] cachedElements = chunkedList.toArray(Integer[]::new);
      Assertions.assertArrayEquals(elements, cachedElements);
   }

   @Test
   public void shouldToArrayWithIndexReturnElementsAccordingToAddOrder() {
      final int messages = ELEMENTS;
      final Integer[] elements = new Integer[messages];
      for (int i = 0; i < messages; i++) {
         final Integer element = i;
         elements[i] = element;
         chunkedList.add(element);
      }
      final int offset = 10;
      final Integer[] cachedElements = chunkedList.toArray(size -> new Integer[offset + size], offset);
      Assertions.assertArrayEquals(elements, Arrays.copyOfRange(cachedElements, offset, cachedElements.length));
      Assertions.assertArrayEquals(new Integer[offset], Arrays.copyOfRange(cachedElements, 0, offset));
   }

	@Test
   public void shouldFailToArrayWithInsufficientArrayCapacity() {
		Assertions.assertThrows(ArrayIndexOutOfBoundsException.class, () -> {
         final int messages = ELEMENTS;
         final Integer[] elements = new Integer[messages];
         for (int i = 0; i < messages; i++) {
            final Integer element = i;
            elements[i] = element;
            chunkedList.add(element);
         }
         final int offset = 10;
         chunkedList.toArray(size -> new Integer[offset + size - 1], offset);
      });
   }

	@Test
   public void shouldFailToArrayWithNegativeStartIndex() {
		Assertions.assertThrows(ArrayIndexOutOfBoundsException.class, () -> {
         chunkedList.toArray(Integer[]::new, -1);
      });
   }

	@Test
   public void shouldFailToArrayWithNullArray() {
		Assertions.assertThrows(NullPointerException.class, () -> {
         chunkedList.toArray(size -> null);
      });
   }

   @Test
   public void shouldToArrayReturnElementsAccordingToAddAllOrder() {
      final int messages = ELEMENTS;
      final Integer[] elements = new Integer[messages];
      for (int i = 0; i < messages; i++) {
         final Integer element = i;
         elements[i] = element;
      }
      chunkedList.addAll(elements);
      final Integer[] cachedElements = chunkedList.toArray(Integer[]::new);
      Assertions.assertArrayEquals(elements, cachedElements);
   }

   @Test
   public void shouldToArrayReturnEmptyArrayIfEmpty() {
      final Integer[] array = chunkedList.toArray(Integer[]::new);
      Assertions.assertArrayEquals(new Integer[0], array);
   }

}
