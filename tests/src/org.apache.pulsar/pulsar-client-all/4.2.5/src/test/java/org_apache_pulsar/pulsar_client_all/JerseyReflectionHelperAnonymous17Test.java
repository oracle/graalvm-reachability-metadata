/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.apache.pulsar.shade.org.glassfish.jersey.internal.util.ReflectionHelper;
import org.junit.jupiter.api.Test;

public class JerseyReflectionHelperAnonymous17Test {
    @Test
    void findsExactMethodOnImplementationClass() throws Exception {
        Method contractMethod = MessageContract.class.getMethod("message", String.class);

        Method implementationMethod =
                ReflectionHelper.findMethodOnClassPA(MessageImplementation.class, contractMethod).run();

        assertThat(implementationMethod).isEqualTo(MessageImplementation.class.getMethod("message", String.class));
    }

    @Test
    void searchesPublicMethodsWhenExactSignatureIsAbsent() throws Exception {
        Method genericMethod = GenericContract.class.getMethod("accept", Object.class);

        Method resolved = ReflectionHelper.findMethodOnClassPA(StringImplementation.class, genericMethod).run();

        assertThat(resolved).isNull();
    }

    public interface MessageContract {
        String message(String value);
    }

    public static class MessageImplementation implements MessageContract {
        @Override
        public String message(String value) {
            return value;
        }
    }

    public interface GenericContract<T> {
        void accept(T value);
    }

    public static class StringImplementation {
        public void accept(String value) {
        }
    }
}
