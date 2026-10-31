/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.util.List;

import io.netty.util.internal.TypeParameterMatcher;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ReflectionUtilTest {
    @Test
    void matchesArraysOfParameterizedComponents() {
        TypeParameterMatcher matcher = TypeParameterMatcher.find(new ListArrayHandler(), Handler.class, "T");
        Assertions.assertTrue(matcher.match(new List<?>[] {List.of("payload")}));
        Assertions.assertTrue(matcher.match(new List<?>[0]));
        Assertions.assertFalse(matcher.match(new String[] {"payload"}));
        Assertions.assertFalse(matcher.match(List.of("payload")));
        Assertions.assertFalse(matcher.match(null));
    }

    public abstract static class Handler<T> {
    }

    public static final class ListArrayHandler extends Handler<List<String>[]> {
    }
}
