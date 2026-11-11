/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_grpc.grpc_netty_shaded;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.grpc.netty.shaded.io.netty.channel.embedded.EmbeddedChannel;
import io.grpc.netty.shaded.io.netty.util.Attribute;
import io.grpc.netty.shaded.io.netty.util.AttributeKey;

public class DefaultAttributeMapInnerDefaultAttributeTest {
    @Test
    void channelAttributesCanBeUpdatedRemovedAndRecreated() {
        EmbeddedChannel channel = new EmbeddedChannel();
        AttributeKey<String> key = AttributeKey.valueOf("grpc.metadata.request-context");
        try {
            Attribute<String> attribute = channel.attr(key);
            attribute.set("first");
            assertTrue(attribute.compareAndSet("first", "second"));
            assertEquals("second", channel.attr(key).get());
            assertEquals("second", attribute.getAndRemove());
            assertFalse(channel.hasAttr(key));
            Attribute<String> replacement = channel.attr(key);
            assertNotSame(attribute, replacement);
            replacement.set("new request");
            assertEquals("new request", replacement.get());
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
