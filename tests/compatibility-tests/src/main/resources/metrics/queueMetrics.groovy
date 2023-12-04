package metrics
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

import org.apache.activemq.artemis.tests.compatibility.GroovyRun;

import org.apache.activemq.artemis.api.core.management.QueueControl;

//validate metrics are recovered
Object[] queueControls = server.getJMSServerManager().getActiveMQServer().getManagementService().getResources(QueueControl.class);
for (Object o : queueControls) {
    QueueControl c = (QueueControl) o;
    if (c.isInternal()) {
        continue
    }
    GroovyRun.assertTrue(c.getPersistentSize() > 0);
    GroovyRun.assertTrue(c.getDurablePersistentSize() > 0);
    GroovyRun.assertEquals(33l, c.getMessageCount());
    GroovyRun.assertEquals(33l, c.getDurableMessageCount());
 }
