/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_expression;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.MethodExecutor;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.support.ReflectiveMethodExecutor;
import org.springframework.expression.spel.support.ReflectiveMethodResolver;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectiveMethodExecutorTest {
    @Test
    void invokesResolvedPublicMethodAndDiscoversPublicDeclaringClass() throws Exception {
        StandardEvaluationContext context = new StandardEvaluationContext();
        PublicGreeter greeter = new PublicGreeter("Hello");
        MethodExecutor methodExecutor = new ReflectiveMethodResolver().resolve(
                context,
                greeter,
                "greet",
                List.of(TypeDescriptor.valueOf(String.class)));

        assertThat(methodExecutor)
                .isInstanceOf(ReflectiveMethodExecutor.class);
        ReflectiveMethodExecutor executor = (ReflectiveMethodExecutor) methodExecutor;

        assertThat(executor.getPublicDeclaringClass())
                .isEqualTo(PublicGreeter.class);

        TypedValue result = executor.execute(context, greeter, "Spring");

        assertThat(result.getValue())
                .isEqualTo("Hello, Spring");
        assertThat(executor.didArgumentConversionOccur())
                .isFalse();
    }

    public static final class PublicGreeter {
        private final String salutation;

        public PublicGreeter(String salutation) {
            this.salutation = salutation;
        }

        public String greet(String name) {
            return this.salutation + ", " + name;
        }
    }
}
