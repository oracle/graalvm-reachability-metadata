/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogChute;
import org.apache.velocity.util.introspection.SecureIntrospectorImpl;
import org.junit.jupiter.api.Test;

public class SecureIntrospectorImplTest {
    @Test
    void enforcesSecureMethodAccessRules() throws Exception {
        final SecureIntrospectorImpl introspector = new SecureIntrospectorImpl(
                new String[] {RestrictedOperations.class.getName()},
                new String[] {"java.lang.reflect"},
                new Log(new NullLogChute()));

        assertThat(introspector.checkObjectExecutePermission(Integer.class, "intValue")).isTrue();
        assertThat(introspector.checkObjectExecutePermission(Boolean.class, "booleanValue")).isTrue();
        assertThat(introspector.checkObjectExecutePermission(String.class, "trim")).isTrue();
        assertThat(introspector.checkObjectExecutePermission(Class.class, "getName")).isTrue();
        assertThat(introspector.checkObjectExecutePermission(AllowedOperations.class, "wait")).isFalse();
        assertThat(introspector.checkObjectExecutePermission(AllowedOperations.class, "notify")).isFalse();
        assertThat(introspector.checkObjectExecutePermission(Method.class, "invoke")).isFalse();
        assertThat(introspector.checkObjectExecutePermission(RestrictedOperations.class, "allowed")).isFalse();
    }

    @Test
    void returnsOnlyPermittedMethods() {
        final SecureIntrospectorImpl introspector = new SecureIntrospectorImpl(
                new String[] {RestrictedOperations.class.getName()},
                new String[] {"java.lang.reflect"},
                new Log(new NullLogChute()));

        assertThat(introspector.getMethod(AllowedOperations.class, "allowed", new Object[0])).isNotNull();
        assertThat(introspector.getMethod(AllowedOperations.class, "wait", new Object[0])).isNull();
        assertThat(introspector.getMethod(RestrictedOperations.class, "allowed", new Object[0])).isNull();
    }

    public static class AllowedOperations {
        public String allowed() {
            return "allowed";
        }
    }

    public static class RestrictedOperations {
        public String allowed() {
            return "restricted";
        }
    }
}
