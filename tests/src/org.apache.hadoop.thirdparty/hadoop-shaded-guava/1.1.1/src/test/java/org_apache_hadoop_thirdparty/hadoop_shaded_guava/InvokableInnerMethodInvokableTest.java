/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hadoop_thirdparty.hadoop_shaded_guava;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.apache.hadoop.thirdparty.com.google.common.reflect.Invokable;
import org.junit.jupiter.api.Test;

public class InvokableInnerMethodInvokableTest {

    @Test
    void methodInvokableCallsInstanceMethodWithArguments()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = GreetingService.class.getMethod("greet", String.class, int.class);
        @SuppressWarnings("unchecked")
        Invokable<GreetingService, String> invokable =
                (Invokable<GreetingService, String>) (Invokable<?, ?>) Invokable.from(method);

        String greeting = invokable.invoke(new GreetingService("hello"), "hadoop", 2);

        assertThat(greeting).isEqualTo("hello hadoop hadoop");
        assertThat(invokable.getReturnType().getRawType()).isEqualTo(String.class);
        assertThat(invokable.getParameters()).hasSize(2);
    }

    public static final class GreetingService {
        private final String salutation;

        public GreetingService(String salutation) {
            this.salutation = salutation;
        }

        public String greet(String name, int repetitions) {
            StringBuilder builder = new StringBuilder(salutation);
            for (int index = 0; index < repetitions; index++) {
                builder.append(' ').append(name);
            }
            return builder.toString();
        }
    }
}
