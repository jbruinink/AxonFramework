/*
 * Copyright (c) 2010-2011. Axon Framework
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

package org.axonframework.eventstore.legacy;

import org.axonframework.domain.DomainEvent;
import org.axonframework.eventstore.EventSerializer;
import org.axonframework.serializer.Serializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Wrapper for {@link EventSerializer} implementations to make it fit in {@link Serializer}.
 *
 * @author Allard Buijze
 * @since 1.2
 * @deprecated Do not use.
 */
@Deprecated
public class LegacyEventSerializerWrapper implements Serializer<DomainEvent> {
    private final EventSerializer eventSerializer;

    /**
     * Initialize a wrapper for the given <code>eventSerializer</code>.
     *
     * @param eventSerializer the eventSerializer to delegate requests to
     */
    public LegacyEventSerializerWrapper(EventSerializer eventSerializer) {
        this.eventSerializer = eventSerializer;
    }

    @Override
    public void serialize(DomainEvent object, OutputStream outputStream) throws IOException {
        outputStream.write(eventSerializer.serialize(object));
    }

    @Override
    public DomainEvent deserialize(InputStream inputStream) throws IOException {
        throw new UnsupportedOperationException(
                "This operation is not supported when using the "
                        + "deprecated EventSerializer constructor");
    }

    @Override
    public DomainEvent deserialize(byte[] bytes) {
        return eventSerializer.deserialize(bytes);
    }

    @Override
    public byte[] serialize(DomainEvent object) {
        return eventSerializer.serialize(object);
    }
}