/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_redis;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.redis.ArrayRedisMessage;
import io.netty.handler.codec.redis.ErrorRedisMessage;
import io.netty.handler.codec.redis.FullBulkStringRedisMessage;
import io.netty.handler.codec.redis.IntegerRedisMessage;
import io.netty.handler.codec.redis.RedisArrayAggregator;
import io.netty.handler.codec.redis.RedisBulkStringAggregator;
import io.netty.handler.codec.redis.RedisDecoder;
import io.netty.handler.codec.redis.RedisEncoder;
import io.netty.handler.codec.redis.SimpleStringRedisMessage;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class RedisArrayAggregatorTest {
    @Test
    void assemblesNestedArraysOnlyAfterAllChildrenArrive() {
        EmbeddedChannel channel = new EmbeddedChannel(new RedisEncoder(), new RedisDecoder(),
                new RedisBulkStringAggregator(), new RedisArrayAggregator());
        try {
            assertThat(channel.writeInbound(Unpooled.copiedBuffer(
                    "*3\r\n+results\r\n*2\r\n:7\r\n$3\r\nA", CharsetUtil.UTF_8))).isFalse();
            assertThat((Object) channel.readInbound()).isNull();
            assertThat(channel.writeInbound(Unpooled.copiedBuffer(
                    "da\r\n-ERR missing customer\r\n", CharsetUtil.UTF_8))).isTrue();

            ArrayRedisMessage result = channel.readInbound();
            try {
                assertThat(result.children()).hasSize(3);
                SimpleStringRedisMessage label = (SimpleStringRedisMessage) result.children().get(0);
                ArrayRedisMessage row = (ArrayRedisMessage) result.children().get(1);
                ErrorRedisMessage error = (ErrorRedisMessage) result.children().get(2);
                assertThat(label.content()).isEqualTo("results");
                assertThat(row.children()).hasSize(2);
                IntegerRedisMessage id = (IntegerRedisMessage) row.children().get(0);
                FullBulkStringRedisMessage name = (FullBulkStringRedisMessage) row.children().get(1);
                assertThat(id.value()).isEqualTo(7L);
                assertThat(name.content().toString(CharsetUtil.UTF_8)).isEqualTo("Ada");
                assertThat(error.content()).isEqualTo("ERR missing customer");
                assertThat((Object) channel.readInbound()).isNull();
            } finally {
                result.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void preservesNullAndEmptyChildrenAndContinuesWithTheNextReply() {
        EmbeddedChannel channel = new EmbeddedChannel(new RedisEncoder(), new RedisDecoder(),
                new RedisBulkStringAggregator(), new RedisArrayAggregator());
        try {
            assertThat(channel.writeInbound(Unpooled.copiedBuffer(
                    "*4\r\n*-1\r\n*0\r\n$-1\r\n$0\r\n\r\n+PONG\r\n", CharsetUtil.UTF_8))).isTrue();

            ArrayRedisMessage result = channel.readInbound();
            SimpleStringRedisMessage next = channel.readInbound();
            try {
                assertThat(result.children()).hasSize(4);
                ArrayRedisMessage absentArray = (ArrayRedisMessage) result.children().get(0);
                ArrayRedisMessage emptyArray = (ArrayRedisMessage) result.children().get(1);
                FullBulkStringRedisMessage absentString = (FullBulkStringRedisMessage) result.children().get(2);
                FullBulkStringRedisMessage emptyString = (FullBulkStringRedisMessage) result.children().get(3);
                assertThat(absentArray.isNull()).isTrue();
                assertThat(emptyArray.isNull()).isFalse();
                assertThat(emptyArray.children()).isEmpty();
                assertThat(absentString.isNull()).isTrue();
                assertThat(emptyString.isNull()).isFalse();
                assertThat(emptyString.content().readableBytes()).isZero();
                assertThat(next.content()).isEqualTo("PONG");
                assertThat((Object) channel.readInbound()).isNull();
            } finally {
                result.release();
            }
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
