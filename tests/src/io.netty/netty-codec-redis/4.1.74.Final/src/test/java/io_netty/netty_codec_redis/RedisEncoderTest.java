/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_codec_redis;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.redis.ArrayHeaderRedisMessage;
import io.netty.handler.codec.redis.ArrayRedisMessage;
import io.netty.handler.codec.redis.BulkStringHeaderRedisMessage;
import io.netty.handler.codec.redis.DefaultBulkStringRedisContent;
import io.netty.handler.codec.redis.DefaultLastBulkStringRedisContent;
import io.netty.handler.codec.redis.ErrorRedisMessage;
import io.netty.handler.codec.redis.FullBulkStringRedisMessage;
import io.netty.handler.codec.redis.InlineCommandRedisMessage;
import io.netty.handler.codec.redis.IntegerRedisMessage;
import io.netty.handler.codec.redis.RedisEncoder;
import io.netty.handler.codec.redis.SimpleStringRedisMessage;
import io.netty.util.CharsetUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class RedisEncoderTest {
    @Test
    void encodesInlineCommandsAndScalarReplies() {
        EmbeddedChannel channel = new EmbeddedChannel(new RedisEncoder());
        try {
            assertThat(channel.writeOutbound(
                    new InlineCommandRedisMessage("PING"),
                    new SimpleStringRedisMessage("OK"),
                    new ErrorRedisMessage("ERR missing customer"),
                    new IntegerRedisMessage(42),
                    new IntegerRedisMessage(-9000))).isTrue();

            assertThat(readWire(channel)).isEqualTo("PING\r\n+OK\r\n-ERR missing customer\r\n:42\r\n:-9000\r\n");
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void encodesASetCommandAsAnArrayOfBulkStrings() {
        EmbeddedChannel channel = new EmbeddedChannel(new RedisEncoder());
        try {
            ArrayRedisMessage command = new ArrayRedisMessage(List.of(
                    new FullBulkStringRedisMessage(Unpooled.copiedBuffer("SET", CharsetUtil.UTF_8)),
                    new FullBulkStringRedisMessage(Unpooled.copiedBuffer("customer:7", CharsetUtil.UTF_8)),
                    new FullBulkStringRedisMessage(Unpooled.copiedBuffer("Ada", CharsetUtil.UTF_8))));
            assertThat(channel.writeOutbound(command)).isTrue();

            assertThat(readWire(channel)).isEqualTo("*3\r\n$3\r\nSET\r\n$10\r\ncustomer:7\r\n$3\r\nAda\r\n");
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void encodesStreamingArrayAndBulkStringParts() {
        EmbeddedChannel channel = new EmbeddedChannel(new RedisEncoder());
        try {
            assertThat(channel.writeOutbound(
                    new ArrayHeaderRedisMessage(2),
                    new FullBulkStringRedisMessage(Unpooled.copiedBuffer("ECHO", CharsetUtil.UTF_8)),
                    new BulkStringHeaderRedisMessage(7),
                    new DefaultBulkStringRedisContent(Unpooled.copiedBuffer("red", CharsetUtil.UTF_8)),
                    new DefaultLastBulkStringRedisContent(Unpooled.copiedBuffer("wood", CharsetUtil.UTF_8))))
                    .isTrue();

            assertThat(readWire(channel)).isEqualTo("*2\r\n$4\r\nECHO\r\n$7\r\nredwood\r\n");
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void distinguishesNullAndEmptyBulkStringsAndArrays() {
        EmbeddedChannel channel = new EmbeddedChannel(new RedisEncoder());
        try {
            assertThat(channel.writeOutbound(
                    FullBulkStringRedisMessage.NULL_INSTANCE,
                    FullBulkStringRedisMessage.EMPTY_INSTANCE,
                    ArrayRedisMessage.NULL_INSTANCE,
                    ArrayRedisMessage.EMPTY_INSTANCE)).isTrue();

            assertThat(readWire(channel)).isEqualTo("$-1\r\n$0\r\n\r\n*-1\r\n*0\r\n");
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    private static String readWire(EmbeddedChannel channel) {
        StringBuilder wire = new StringBuilder();
        ByteBuf part = channel.readOutbound();
        while (part != null) {
            try {
                wire.append(part.toString(CharsetUtil.UTF_8));
            } finally {
                part.release();
            }
            part = channel.readOutbound();
        }
        return wire.toString();
    }
}
