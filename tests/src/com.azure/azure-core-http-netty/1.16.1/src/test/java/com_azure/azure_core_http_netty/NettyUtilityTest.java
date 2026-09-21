/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core_http_netty;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.azure.core.http.netty.implementation.NettyUtility;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class NettyUtilityTest {
    @Test
    void validatesClasspathVersionsAndCopiesBuffersIndependently() {
        ByteBuf source = Unpooled.wrappedBuffer("netty-data".getBytes(UTF_8));
        try {
            ByteBuffer copy = NettyUtility.deepCopyBuffer(source);
            source.setByte(0, 'N');

            assertThat(copy).isEqualTo(ByteBuffer.wrap("netty-data".getBytes(UTF_8)));
            assertThatCode(NettyUtility::validateNettyVersions).doesNotThrowAnyException();
        } finally {
            assertThat(source.release()).isTrue();
        }
    }
}
