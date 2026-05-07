/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_common;

import static org.assertj.core.api.Assertions.assertThat;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import org.apache.pulsar.common.protocol.ByteBufPair;
import org.junit.jupiter.api.Test;

public class ByteBufPairTest {
    @Test
    void getEncoderInitializesTlsCompatibilityCheck() {
        ChannelOutboundHandlerAdapter plaintextEncoder = ByteBufPair.getEncoder(false);
        ChannelOutboundHandlerAdapter tlsEncoder = ByteBufPair.getEncoder(true);

        assertThat(plaintextEncoder).isSameAs(ByteBufPair.ENCODER);
        assertThat(tlsEncoder).isNotNull();
    }

    @Test
    void coalesceCombinesReadableBytesAndReleasesPair() {
        ByteBuf first = Unpooled.copiedBuffer(new byte[] {1, 2});
        ByteBuf second = Unpooled.copiedBuffer(new byte[] {3, 4, 5});
        ByteBufPair pair = ByteBufPair.get(first, second);

        ByteBuf coalesced = ByteBufPair.coalesce(pair);
        try {
            assertThat(pair.refCnt()).isZero();
            assertThat(first.refCnt()).isZero();
            assertThat(second.refCnt()).isZero();
            byte[] actual = new byte[5];
            assertThat(coalesced.readableBytes()).isEqualTo(actual.length);
            coalesced.readBytes(actual);
            assertThat(actual).containsExactly(1, 2, 3, 4, 5);
        } finally {
            coalesced.release();
        }
    }
}
