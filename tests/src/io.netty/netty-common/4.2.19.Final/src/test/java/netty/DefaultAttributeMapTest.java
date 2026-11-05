/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.DefaultAttributeMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DefaultAttributeMapTest {
    @Test
    void storesTypedAttributesIndependentlyForEachMap() {
        AttributeKey<String> sessionKey = AttributeKey.valueOf(DefaultAttributeMapTest.class, "session");
        AttributeKey<Integer> attemptsKey = AttributeKey.valueOf(DefaultAttributeMapTest.class, "attempts");
        DefaultAttributeMap first = new DefaultAttributeMap();
        DefaultAttributeMap second = new DefaultAttributeMap();
        Assertions.assertFalse(first.hasAttr(sessionKey));
        first.attr(attemptsKey).set(3);
        Attribute<String> session = first.attr(sessionKey);
        Assertions.assertSame(sessionKey, session.key());
        Assertions.assertNull(session.setIfAbsent("initial"));
        Assertions.assertEquals("initial", session.setIfAbsent("ignored"));
        Assertions.assertSame(session, first.attr(sessionKey));
        Assertions.assertTrue(first.hasAttr(sessionKey));
        Assertions.assertEquals(Integer.valueOf(3), first.attr(attemptsKey).get());

        String initial = session.get();
        Assertions.assertTrue(session.compareAndSet(initial, "authenticated"));
        Assertions.assertFalse(session.compareAndSet(initial, "stale"));
        Assertions.assertEquals("authenticated", session.getAndSet(null));
        Assertions.assertNull(session.get());
        Assertions.assertTrue(first.hasAttr(sessionKey));

        Assertions.assertFalse(second.hasAttr(sessionKey));
        second.attr(sessionKey).set("other-session");
        Assertions.assertEquals("other-session", second.attr(sessionKey).get());
        Assertions.assertNull(first.attr(sessionKey).get());
        Assertions.assertFalse(second.hasAttr(attemptsKey));
    }
}
