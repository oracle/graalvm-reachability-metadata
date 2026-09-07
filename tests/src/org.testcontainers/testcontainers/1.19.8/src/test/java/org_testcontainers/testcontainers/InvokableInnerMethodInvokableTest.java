/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.google.common.reflect.Invokable;
import org.testcontainers.shaded.com.google.common.reflect.TypeToken;

import static org.assertj.core.api.Assertions.assertThat;

public class InvokableInnerMethodInvokableTest {
    @Test
    void invokesAMethodRepresentedByAnInvokable() throws Exception {
        Invokable<Service, Object> method = TypeToken.of(Service.class)
            .method(Service.class.getMethod("greet", String.class));

        assertThat(method.invoke(new Service(), "Ada")).isEqualTo("Hello Ada");
    }

    public static class Service {
        public String greet(String name) {
            return "Hello " + name;
        }
    }
}
