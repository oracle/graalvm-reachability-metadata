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
import java.nio.charset.StandardCharsets;
import org.apache.pulsar.common.protocol.ByteBufPair;
import org.junit.jupiter.api.Test;

public class ByteBufPairTest {

    @Test
    void coalescesBothBuffersAndReleasesPair() {
        final ByteBuf first = Unpooled.copiedBuffer("pulsar", StandardCharsets.UTF_8);
        final ByteBuf second = Unpooled.copiedBuffer("-common", StandardCharsets.UTF_8);
        final ByteBufPair pair = ByteBufPair.get(first, second);

        assertThat(pair.getFirst()).isSameAs(first);
        assertThat(pair.getSecond()).isSameAs(second);
        assertThat(pair.readableBytes()).isEqualTo("pulsar-common".length());

        final ByteBuf coalesced = ByteBufPair.coalesce(pair);
        try {
            assertThat(coalesced.toString(StandardCharsets.UTF_8)).isEqualTo("pulsar-common");
            assertThat(pair.refCnt()).isZero();
            assertThat(first.refCnt()).isZero();
            assertThat(second.refCnt()).isZero();
        } finally {
            coalesced.release();
        }
    }

    @Test
    void selectsSharedEncoderForPlainConnections() {
        final ChannelOutboundHandlerAdapter encoder = ByteBufPair.getEncoder(false);

        assertThat(encoder).isInstanceOf(ByteBufPair.Encoder.class);
    }
}
