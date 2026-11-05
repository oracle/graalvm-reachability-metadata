/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_couchbase_client.core_io;

import com.couchbase.client.core.deps.io.netty.buffer.Unpooled;
import com.couchbase.client.core.deps.io.netty.channel.embedded.EmbeddedChannel;
import com.couchbase.client.core.deps.io.netty.handler.codec.string.StringDecoder;
import com.couchbase.client.core.deps.io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ChannelHandlerMaskAnonymous2Test {
    @Test
    void exercisesPublicApi() throws Exception {
        EmbeddedChannel channel = new EmbeddedChannel(new StringDecoder(CharsetUtil.UTF_8));
        try {
            assertTrue(channel.writeInbound(Unpooled.copiedBuffer("message", CharsetUtil.UTF_8)));
            assertEquals("message", channel.readInbound());
            assertNull(channel.readInbound());
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
