/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.io.netty.buffer.ByteBuf;
import com.couchbase.client.core.deps.io.netty.channel.embedded.EmbeddedChannel;
import com.couchbase.client.core.deps.io.netty.handler.codec.serialization.ClassResolvers;
import com.couchbase.client.core.deps.io.netty.handler.codec.serialization.ObjectDecoder;
import com.couchbase.client.core.deps.io.netty.handler.codec.serialization.ObjectDecoderInputStream;
import com.couchbase.client.core.deps.io.netty.handler.codec.serialization.ObjectEncoder;
import com.couchbase.client.core.deps.io.netty.handler.codec.serialization.ObjectEncoderOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;

final class NettySerializationSupport {
    private NettySerializationSupport() {
    }

    static Object throughChannels(Serializable value) {
        EmbeddedChannel encoder = new EmbeddedChannel(new ObjectEncoder());
        EmbeddedChannel decoder = new EmbeddedChannel(new ObjectDecoder(ClassResolvers.cacheDisabled(NettySerializationSupport.class.getClassLoader())));
        try {
            encoder.writeOutbound(value);
            ByteBuf encoded = encoder.readOutbound();
            decoder.writeInbound(encoded);
            return decoder.readInbound();
        } finally {
            encoder.finishAndReleaseAll();
            decoder.finishAndReleaseAll();
        }
    }

    static Object throughStreams(Object value) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectEncoderOutputStream output = new ObjectEncoderOutputStream(bytes)) {
            output.writeObject(value);
        }
        try (ObjectDecoderInputStream input = new ObjectDecoderInputStream(new ByteArrayInputStream(bytes.toByteArray()), NettySerializationSupport.class.getClassLoader())) {
            return input.readObject();
        }
    }
}
