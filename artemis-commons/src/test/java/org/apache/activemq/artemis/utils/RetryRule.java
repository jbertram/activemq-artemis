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

import org.junit.jupiter.api.extension.TestWatcher;

/**
 * Please use this only if you have to.
 * Try to fix the test first.
 */
public class RetryRule implements TestWatcher {
   // TODO: replace this with some other method, e.g.:
   //  - https://github.com/artsok/rerunner-jupiter/
   //  - https://issues.apache.org/jira/browse/SUREFIRE-1584
   //  - https://github.com/junit-pioneer/junit-pioneer
   //  Additional discussion at https://github.com/junit-team/junit5/issues/1558
}