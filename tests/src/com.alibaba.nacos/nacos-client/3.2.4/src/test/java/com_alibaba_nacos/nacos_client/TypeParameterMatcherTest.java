/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.channel.ChannelHandlerContext;
import com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.handler.codec.MessageToMessageDecoder;
import java.util.List;
import org.junit.jupiter.api.Test;

public class TypeParameterMatcherTest {
    public static class ListsDecoder extends MessageToMessageDecoder<List<String>[]> {
        @Override
        protected void decode(ChannelHandlerContext context, List<String>[] message, List<Object> output) {
            output.add(message[0].size());
        }
    }

    @Test
    void resolvesParameterizedArrayDecoderTypes() throws Exception {
        ListsDecoder decoder = new ListsDecoder();
        assertTrue(decoder.acceptInboundMessage(new List<?>[] {List.of("config", "naming")}));
        assertFalse(decoder.acceptInboundMessage("config"));
        assertFalse(decoder.acceptInboundMessage(new String[] {"config"}));
    }
}
