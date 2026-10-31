/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.io.netty.channel.ChannelHandlerContext;
import com.couchbase.client.core.deps.io.netty.channel.embedded.EmbeddedChannel;
import com.couchbase.client.core.deps.io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReflectionUtilTest {
    @Test
    void decodesArraysOfParameterizedElements() {
        EmbeddedChannel channel = new EmbeddedChannel(new ListArrayDecoder());
        try {
            @SuppressWarnings("unchecked")
            List<String>[] values = (List<String>[]) new List<?>[]{List.of("alpha"), List.of("beta")};
            assertTrue(channel.writeInbound((Object) values));
            assertEquals("alpha/beta", channel.readInbound());
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    public static class ListArrayDecoder extends MessageToMessageDecoder<List<String>[]> {
        @Override
        protected void decode(ChannelHandlerContext context, List<String>[] values, List<Object> output) {
            output.add(values[0].get(0) + "/" + values[1].get(0));
        }
    }
}
