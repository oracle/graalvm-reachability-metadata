/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_redis;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.redis.BulkStringHeaderRedisMessage;
import io.netty.handler.codec.redis.BulkStringRedisContent;
import io.netty.handler.codec.redis.ErrorRedisMessage;
import io.netty.handler.codec.redis.FixedRedisMessagePool;
import io.netty.handler.codec.redis.InlineCommandRedisMessage;
import io.netty.handler.codec.redis.IntegerRedisMessage;
import io.netty.handler.codec.redis.LastBulkStringRedisContent;
import io.netty.handler.codec.redis.RedisDecoder;
import io.netty.handler.codec.redis.SimpleStringRedisMessage;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class RedisDecoderTest {
    @Test
    void decodesPipelinedRepliesWithCachedAndUncachedValues() {
        EmbeddedChannel channel = new EmbeddedChannel(new RedisDecoder());
        try {
            assertThat(channel.writeInbound(Unpooled.copiedBuffer(
                    "+OK\r\n+ready\r\n-ERR syntax error\r\n-ERR missing customer\r\n:42\r\n:-9000\r\n",
                    CharsetUtil.UTF_8))).isTrue();

            SimpleStringRedisMessage ok = channel.readInbound();
            SimpleStringRedisMessage ready = channel.readInbound();
            ErrorRedisMessage syntax = channel.readInbound();
            ErrorRedisMessage missing = channel.readInbound();
            IntegerRedisMessage count = channel.readInbound();
            IntegerRedisMessage negative = channel.readInbound();
            assertThat(ok.content()).isEqualTo("OK");
            assertThat(ok).isSameAs(FixedRedisMessagePool.INSTANCE.getSimpleString("OK"));
            assertThat(ready.content()).isEqualTo("ready");
            assertThat(syntax.content()).isEqualTo("ERR syntax error");
            assertThat(missing.content()).isEqualTo("ERR missing customer");
            assertThat(count.value()).isEqualTo(42L);
            assertThat(negative.value()).isEqualTo(-9000L);
            assertThat((Object) channel.readInbound()).isNull();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void decodesInlineCommandsAcrossNetworkFragmentsWhenEnabled() {
        EmbeddedChannel channel = new EmbeddedChannel(new RedisDecoder(true));
        try {
            assertThat(channel.writeInbound(Unpooled.copiedBuffer("PI", CharsetUtil.UTF_8))).isFalse();
            assertThat(channel.writeInbound(Unpooled.copiedBuffer("NG\r", CharsetUtil.UTF_8))).isFalse();
            assertThat(channel.writeInbound(Unpooled.copiedBuffer("\n+PONG\r\n", CharsetUtil.UTF_8))).isTrue();

            InlineCommandRedisMessage command = channel.readInbound();
            SimpleStringRedisMessage response = channel.readInbound();
            assertThat(command.content()).isEqualTo("PING");
            assertThat(response.content()).isEqualTo("PONG");
            assertThat((Object) channel.readInbound()).isNull();
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void exposesBulkStringHeaderAndContentChunksForStreamingConsumers() {
        EmbeddedChannel channel = new EmbeddedChannel(new RedisDecoder());
        try {
            assertThat(channel.writeInbound(Unpooled.copiedBuffer("$7\r\nred", CharsetUtil.UTF_8))).isTrue();
            BulkStringHeaderRedisMessage header = channel.readInbound();
            BulkStringRedisContent first = channel.readInbound();
            try {
                assertThat(header.bulkStringLength()).isEqualTo(7);
                assertThat(first).isNotInstanceOf(LastBulkStringRedisContent.class);
                assertThat(first.content().toString(CharsetUtil.UTF_8)).isEqualTo("red");
            } finally {
                first.release();
            }
            assertThat((Object) channel.readInbound()).isNull();

            assertThat(channel.writeInbound(Unpooled.copiedBuffer("wood\r\n", CharsetUtil.UTF_8))).isTrue();
            LastBulkStringRedisContent last = channel.readInbound();
            try {
                assertThat(last.content().toString(CharsetUtil.UTF_8)).isEqualTo("wood");
                assertThat((Object) channel.readInbound()).isNull();
            } finally {
                last.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
