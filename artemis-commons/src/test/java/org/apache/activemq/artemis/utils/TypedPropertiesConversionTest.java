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

import org.apache.activemq.artemis.api.core.ActiveMQPropertyConversionException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.utils.collections.TypedProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TypedPropertiesConversionTest {


   private TypedProperties props;

   private SimpleString key;

   private final SimpleString unknownKey = new SimpleString("this.key.is.never.used");

   @BeforeEach
   public void setUp() throws Exception {
      key = RandomUtil.randomSimpleString();
      props = new TypedProperties();
   }

   @Test
   public void testBooleanProperty() throws Exception {
      Boolean val = RandomUtil.randomBoolean();
      props.putBooleanProperty(key, val);

      Assertions.assertEquals(val, props.getBooleanProperty(key));
      Assertions.assertEquals(new SimpleString(Boolean.toString(val)), props.getSimpleStringProperty(key));

      props.putSimpleStringProperty(key, new SimpleString(Boolean.toString(val)));
      Assertions.assertEquals(val, props.getBooleanProperty(key));

      try {
         props.putByteProperty(key, RandomUtil.randomByte());
         props.getBooleanProperty(key);
         Assertions.fail();
      } catch (ActiveMQPropertyConversionException e) {
      }

      Assertions.assertFalse(props.getBooleanProperty(unknownKey));
   }

   @Test
   public void testCharProperty() throws Exception {
      Character val = RandomUtil.randomChar();
      props.putCharProperty(key, val);

      Assertions.assertEquals(val, props.getCharProperty(key));
      Assertions.assertEquals(new SimpleString(Character.toString(val)), props.getSimpleStringProperty(key));

      try {
         props.putByteProperty(key, RandomUtil.randomByte());
         props.getCharProperty(key);
         Assertions.fail();
      } catch (ActiveMQPropertyConversionException e) {
      }

      try {
         props.getCharProperty(unknownKey);
         Assertions.fail();
      } catch (NullPointerException e) {
      }
   }

   @Test
   public void testByteProperty() throws Exception {
      Byte val = RandomUtil.randomByte();
      props.putByteProperty(key, val);

      Assertions.assertEquals(val, props.getByteProperty(key));
      Assertions.assertEquals(new SimpleString(Byte.toString(val)), props.getSimpleStringProperty(key));

      props.putSimpleStringProperty(key, new SimpleString(Byte.toString(val)));
      Assertions.assertEquals(val, props.getByteProperty(key));

      try {
         props.putBooleanProperty(key, RandomUtil.randomBoolean());
         props.getByteProperty(key);
         Assertions.fail();
      } catch (ActiveMQPropertyConversionException e) {
      }

      try {
         props.getByteProperty(unknownKey);
         Assertions.fail();
      } catch (NumberFormatException e) {
      }
   }

   @Test
   public void testNoByteProperty() {
      Assertions.assertEquals(0, props.size());
      Assertions.assertNull(props.getByteProperty(key, () -> null));
      props.putByteProperty(key.concat('0'), RandomUtil.randomByte());
      Assertions.assertEquals(1, props.size());
      Assertions.assertNull(props.getByteProperty(key, () -> null));
      props.putNullValue(key);
      Assertions.assertTrue(props.containsProperty(key));
      Assertions.assertNull(props.getByteProperty(key, () -> null));
   }

   @Test
   public void testIntProperty() throws Exception {
      Integer val = RandomUtil.randomInt();
      props.putIntProperty(key, val);

      Assertions.assertEquals(val, props.getIntProperty(key));
      Assertions.assertEquals(new SimpleString(Integer.toString(val)), props.getSimpleStringProperty(key));

      props.putSimpleStringProperty(key, new SimpleString(Integer.toString(val)));
      Assertions.assertEquals(val, props.getIntProperty(key));

      Byte byteVal = RandomUtil.randomByte();
      props.putByteProperty(key, byteVal);
      Assertions.assertEquals(Integer.valueOf(byteVal), props.getIntProperty(key));

      try {
         props.putBooleanProperty(key, RandomUtil.randomBoolean());
         props.getIntProperty(key);
         Assertions.fail();
      } catch (ActiveMQPropertyConversionException e) {
      }

      try {
         props.getIntProperty(unknownKey);
         Assertions.fail();
      } catch (NumberFormatException e) {
      }
   }

   @Test
   public void testLongProperty() throws Exception {
      Long val = RandomUtil.randomLong();
      props.putLongProperty(key, val);

      Assertions.assertEquals(val, props.getLongProperty(key));
      Assertions.assertEquals(new SimpleString(Long.toString(val)), props.getSimpleStringProperty(key));

      props.putSimpleStringProperty(key, new SimpleString(Long.toString(val)));
      Assertions.assertEquals(val, props.getLongProperty(key));

      Byte byteVal = RandomUtil.randomByte();
      props.putByteProperty(key, byteVal);
      Assertions.assertEquals(Long.valueOf(byteVal), props.getLongProperty(key));

      Short shortVal = RandomUtil.randomShort();
      props.putShortProperty(key, shortVal);
      Assertions.assertEquals(Long.valueOf(shortVal), props.getLongProperty(key));

      Integer intVal = RandomUtil.randomInt();
      props.putIntProperty(key, intVal);
      Assertions.assertEquals(Long.valueOf(intVal), props.getLongProperty(key));

      try {
         props.putBooleanProperty(key, RandomUtil.randomBoolean());
         props.getLongProperty(key);
         Assertions.fail();
      } catch (ActiveMQPropertyConversionException e) {
      }

      try {
         props.getLongProperty(unknownKey);
         Assertions.fail();
      } catch (NumberFormatException e) {
      }
   }

   @Test
   public void testDoubleProperty() throws Exception {
      Double val = RandomUtil.randomDouble();
      props.putDoubleProperty(key, val);

      Assertions.assertEquals(val, props.getDoubleProperty(key));
      Assertions.assertEquals(new SimpleString(Double.toString(val)), props.getSimpleStringProperty(key));

      props.putSimpleStringProperty(key, new SimpleString(Double.toString(val)));
      Assertions.assertEquals(val, props.getDoubleProperty(key));

      try {
         props.putBooleanProperty(key, RandomUtil.randomBoolean());
         props.getDoubleProperty(key);
         Assertions.fail();
      } catch (ActiveMQPropertyConversionException e) {
      }

      try {
         props.getDoubleProperty(unknownKey);
         Assertions.fail();
      } catch (Exception e) {
      }
   }

   @Test
   public void testFloatProperty() throws Exception {
      Float val = RandomUtil.randomFloat();
      props.putFloatProperty(key, val);

      Assertions.assertEquals(val, props.getFloatProperty(key));
      Assertions.assertEquals(Double.valueOf(val), props.getDoubleProperty(key));
      Assertions.assertEquals(new SimpleString(Float.toString(val)), props.getSimpleStringProperty(key));

      props.putSimpleStringProperty(key, new SimpleString(Float.toString(val)));
      Assertions.assertEquals(val, props.getFloatProperty(key));

      try {
         props.putBooleanProperty(key, RandomUtil.randomBoolean());
         props.getFloatProperty(key);
         Assertions.fail();
      } catch (ActiveMQPropertyConversionException e) {
      }

      try {
         props.getFloatProperty(unknownKey);
         Assertions.fail();
      } catch (Exception e) {
      }
   }

   @Test
   public void testShortProperty() throws Exception {
      Short val = RandomUtil.randomShort();
      props.putShortProperty(key, val);

      Assertions.assertEquals(val, props.getShortProperty(key));
      Assertions.assertEquals(Integer.valueOf(val), props.getIntProperty(key));
      Assertions.assertEquals(new SimpleString(Short.toString(val)), props.getSimpleStringProperty(key));

      props.putSimpleStringProperty(key, new SimpleString(Short.toString(val)));
      Assertions.assertEquals(val, props.getShortProperty(key));

      Byte byteVal = RandomUtil.randomByte();
      props.putByteProperty(key, byteVal);
      Assertions.assertEquals(Short.valueOf(byteVal), props.getShortProperty(key));

      try {
         props.putBooleanProperty(key, RandomUtil.randomBoolean());
         props.getShortProperty(key);
         Assertions.fail();
      } catch (ActiveMQPropertyConversionException e) {
      }

      try {
         props.getShortProperty(unknownKey);
         Assertions.fail();
      } catch (NumberFormatException e) {
      }
   }

   @Test
   public void testSimpleStringProperty() throws Exception {
      SimpleString strVal = RandomUtil.randomSimpleString();
      props.putSimpleStringProperty(key, strVal);
      Assertions.assertEquals(strVal, props.getSimpleStringProperty(key));
   }

   @Test
   public void testBytesProperty() throws Exception {
      byte[] val = RandomUtil.randomBytes();
      props.putBytesProperty(key, val);

      Assertions.assertArrayEquals(val, props.getBytesProperty(key));

      try {
         props.putBooleanProperty(key, RandomUtil.randomBoolean());
         props.getBytesProperty(key);
         Assertions.fail();
      } catch (ActiveMQPropertyConversionException e) {
      }

      Assertions.assertNull(props.getBytesProperty(unknownKey));
   }

}
