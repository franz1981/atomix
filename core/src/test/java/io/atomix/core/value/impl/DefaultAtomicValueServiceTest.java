/*
 * Copyright 2018-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.core.value.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import io.atomix.core.map.AtomicMapType;
import io.atomix.primitive.PrimitiveId;
import io.atomix.primitive.service.ServiceContext;
import io.atomix.primitive.session.Session;
import io.atomix.primitive.session.SessionId;
import io.atomix.utils.time.WallClock;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Atomic value service test.
 */
public class DefaultAtomicValueServiceTest {

  @Test
  @SuppressWarnings("unchecked")
  public void testSnapshot() throws Exception {
    ServiceContext context = mock(ServiceContext.class);
    when(context.serviceType()).thenReturn(AtomicMapType.instance());
    when(context.serviceName()).thenReturn("test");
    when(context.serviceId()).thenReturn(PrimitiveId.from(1));
    when(context.wallClock()).thenReturn(new WallClock());

    Session session = mock(Session.class);
    when(session.sessionId()).thenReturn(SessionId.from(1));

    DefaultAtomicValueService service = new DefaultAtomicValueService();
    service.init(context);

    assertNull(service.get());

    ByteArrayOutputStream os = new ByteArrayOutputStream();
    service.backup(os);

    assertNull(service.get());

    service = new DefaultAtomicValueService();
    service.restore(new ByteArrayInputStream(os.toByteArray()));

    assertNull(service.get());

    service.set("Hello world!".getBytes());
    assertArrayEquals("Hello world!".getBytes(), service.get().value());

    os = new ByteArrayOutputStream();
    service.backup(os);

    assertArrayEquals("Hello world!".getBytes(), service.get().value());

    service = new DefaultAtomicValueService();
    service.restore(new ByteArrayInputStream(os.toByteArray()));

    assertArrayEquals("Hello world!".getBytes(), service.get().value());

    service.set(null);
    assertNull(service.get());
  }
}