/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty_websocket.websocket_common;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.jetty.websocket.common.util.ReflectUtils;
import org.junit.jupiter.api.Test;

public class ReflectUtilsTest {

    @Test
    public void identifiesPublicDefaultConstructableClasses() {
        boolean defaultConstructable = ReflectUtils.isDefaultConstructable(DefaultConstructableEndpoint.class);

        assertThat(defaultConstructable).isTrue();
    }

    @Test
    public void rejectsPublicClassesWithoutDefaultConstructors() {
        boolean defaultConstructable = ReflectUtils.isDefaultConstructable(ConstructorArgumentEndpoint.class);

        assertThat(defaultConstructable).isFalse();
    }

    public static class DefaultConstructableEndpoint {
        public DefaultConstructableEndpoint() {
        }
    }

    public static class ConstructorArgumentEndpoint {
        public ConstructorArgumentEndpoint(String value) {
            assertThat(value).isNotEmpty();
        }
    }
}
