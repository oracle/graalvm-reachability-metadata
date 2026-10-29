/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_redis;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.redis.FullBulkStringRedisMessage;
import io.netty.handler.codec.redis.RedisBulkStringAggregator;
import io.netty.handler.codec.redis.RedisDecoder;
import io.netty.handler.codec.redis.RedisEncoder;
import io.netty.handler.codec.redis.SimpleStringRedisMessage;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class RedisBulkStringAggregatorTest {
    @Test
    void aggregatesBinaryPayloadDeliveredOneByteAtATime() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new RedisEncoder(), new RedisDecoder(), new RedisBulkStringAggregator());
        byte[] wire = new byte[] {'$', '5', '\r', '\n', 'A', 0, '\r', '\n', (byte) 0xff, '\r', '\n'};
        try {
            for (int index = 0; index < wire.length; index++) {
                boolean complete = channel.writeInbound(Unpooled.wrappedBuffer(new byte[] {wire[index]}));
                assertThat(complete).isEqualTo(index == wire.length - 1);
            }
            FullBulkStringRedisMessage message = channel.readInbound();
            try {
                assertThat(message.isNull()).isFalse();
                byte[] payload = new byte[message.content().readableBytes()];
                message.content().getBytes(message.content().readerIndex(), payload);
                assertThat(payload).containsExactly((byte) 'A', (byte) 0, (byte) '\r', (byte) '\n', (byte) 0xff);
                assertThat((Object) channel.readInbound()).isNull();
            } finally {
                message.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void separatesConsecutiveBulkStringReplies() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new RedisEncoder(), new RedisDecoder(), new RedisBulkStringAggregator());
        try {
            assertThat(channel.writeInbound(Unpooled.copiedBuffer(
                    "$3\r\none\r\n$3\r\ntwo\r\n", CharsetUtil.UTF_8))).isTrue();
            FullBulkStringRedisMessage first = channel.readInbound();
            FullBulkStringRedisMessage second = channel.readInbound();
            try {
                assertThat(first.content().toString(CharsetUtil.UTF_8)).isEqualTo("one");
                assertThat(second.content().toString(CharsetUtil.UTF_8)).isEqualTo("two");
                assertThat((Object) channel.readInbound()).isNull();
            } finally {
                first.release();
                second.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void preservesNullAndEmptyBulkStringsAndPassesThroughOtherReplies() {
        EmbeddedChannel channel = new EmbeddedChannel(
                new RedisEncoder(), new RedisDecoder(), new RedisBulkStringAggregator());
        try {
            assertThat(channel.writeInbound(Unpooled.copiedBuffer(
                    "$-1\r\n$0\r\n\r\n+OK\r\n", CharsetUtil.UTF_8))).isTrue();
            FullBulkStringRedisMessage absent = channel.readInbound();
            FullBulkStringRedisMessage empty = channel.readInbound();
            SimpleStringRedisMessage status = channel.readInbound();
            try {
                assertThat(absent.isNull()).isTrue();
                assertThat(empty.isNull()).isFalse();
                assertThat(empty.content().readableBytes()).isZero();
                assertThat(status.content()).isEqualTo("OK");
                assertThat((Object) channel.readInbound()).isNull();
            } finally {
                absent.release();
                empty.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
