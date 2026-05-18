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
import org.springframework.expression.ConstructorExecutor;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.support.ReflectiveConstructorExecutor;
import org.springframework.expression.spel.support.ReflectiveConstructorResolver;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectiveConstructorExecutorTest {
    @Test
    void invokesResolvedPublicConstructor() throws Exception {
        StandardEvaluationContext context = new StandardEvaluationContext();
        ConstructorExecutor constructorExecutor = new ReflectiveConstructorResolver().resolve(
                context,
                ConstructedMessage.class.getName(),
                List.of(TypeDescriptor.valueOf(String.class), TypeDescriptor.valueOf(String.class)));

        assertThat(constructorExecutor)
                .isInstanceOf(ReflectiveConstructorExecutor.class);

        TypedValue result = constructorExecutor.execute(context, "Hello", "Spring");

        assertThat(result.getValue())
                .isInstanceOfSatisfying(ConstructedMessage.class, message -> {
                    assertThat(message.greeting).isEqualTo("Hello");
                    assertThat(message.subject).isEqualTo("Spring");
                });
    }

    public static final class ConstructedMessage {
        private final String greeting;
        private final String subject;

        public ConstructedMessage(String greeting, String subject) {
            this.greeting = greeting;
            this.subject = subject;
        }
    }
}
