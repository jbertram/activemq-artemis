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

import org.junit.Assert;
import org.junit.Test;

import static org.apache.activemq.artemis.utils.PowerOf2Util.align;

public class PowerOf2UtilTest {

   @Test
   public void shouldAlignToNextMultipleOfAlignment() {
      final int alignment = 512;
      Assert.assertEquals(0, align(0, alignment));
      Assert.assertEquals(alignment, align(1, alignment));
      Assert.assertEquals(alignment, align(alignment, alignment));
      Assert.assertEquals(alignment * 2, align(alignment + 1, alignment));

      final int remainder = Integer.MAX_VALUE % alignment;
      final int alignedMax = Integer.MAX_VALUE - remainder;
      Assert.assertEquals(alignedMax, align(alignedMax, alignment));
      //given that Integer.MAX_VALUE is the max value that can be represented with int
      //the aligned value would be > 2^32, but (int)(2^32) = Integer.MIN_VALUE due to the sign bit
      Assert.assertEquals(Integer.MIN_VALUE, align(Integer.MAX_VALUE, alignment));
   }

}
