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
package org.apache.activemq.cli.test;

import java.io.File;
import java.util.stream.Stream;

import io.airlift.airline.ParseArgumentsUnexpectedException;
import org.apache.activemq.artemis.cli.Artemis;
import org.apache.activemq.artemis.cli.commands.ActionContext;
import org.apache.activemq.artemis.cli.commands.InvalidOptionsError;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

public class OptionsValidationTest extends CliTestBase {

   private File artemisInstance;

   @BeforeEach
   @Override
   public void setup() throws Exception {
      super.setup();
      this.artemisInstance = new File(temporaryFolder + "instance1");
   }

   @AfterEach
   @Override
   public void tearDown() throws Exception {
      super.tearDown();
   }
   static Stream<Arguments> parameters() {
      return Stream.of(
         Arguments.of(null, "create", false),
         Arguments.of(null, "run", true),
         Arguments.of(null, "kill", true),
         Arguments.of(null, "stop", true),
         Arguments.of("address", "create", false),
         Arguments.of("address", "delete", false),
         Arguments.of("address", "update", false),
         Arguments.of("address", "show", false),
         Arguments.of(null, "browser", false),
         Arguments.of(null, "consumer", false),
         Arguments.of(null, "mask", false),
         Arguments.of(null, "help", false),
         Arguments.of(null, "migrate1x", false),
         Arguments.of(null, "producer", false),
         Arguments.of("queue", "create", false),
         Arguments.of("queue", "delete", false),
         Arguments.of("queue", "update", false),
         Arguments.of("data", "print", false),
         Arguments.of("data", "print", true),
         Arguments.of("data", "exp", true),
         Arguments.of("data", "imp", true),
         Arguments.of("data", "encode", true),
         Arguments.of("data", "decode", true),
         Arguments.of("data", "compact", true),
         Arguments.of("user", "add", true),
         Arguments.of("user", "rm", true),
         Arguments.of("user", "list", true),
         Arguments.of("user", "reset", true)
      );
   }

   @ParameterizedTest
   @MethodSource("parameters")
   public void testCommand(String group, String command, boolean needInstance) throws Exception {
      ActionContext context = new TestActionContext();
      String[] invalidArgs = null;
      if (group == null) {
         invalidArgs = new String[] {command, "--blahblah-" + command, "--rubbish-" + command + "=" + "more-rubbish", "--input=blahblah"};
      } else {
         invalidArgs = new String[] {group, command, "--blahblah-" + command, "--rubbish-" + command + "=" + "more-rubbish", "--input=blahblah"};
      }
      try {
         Artemis.internalExecute(null, needInstance ? this.artemisInstance : null, invalidArgs, context);
         fail("cannot detect invalid options");
      } catch (InvalidOptionsError e) {
         assertTrue(e.getMessage().contains("Found unexpected parameters"));
      } catch (ParseArgumentsUnexpectedException e) {
         //airline can detect some invalid args during parsing
         //which is fine.
      }
   }
}
