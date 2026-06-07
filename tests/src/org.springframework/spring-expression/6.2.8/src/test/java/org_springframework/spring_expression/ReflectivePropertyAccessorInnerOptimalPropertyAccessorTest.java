/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_expression;

import org.junit.jupiter.api.Test;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.CompilablePropertyAccessor;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectivePropertyAccessorInnerOptimalPropertyAccessorTest {
    @Test
    void readsMethodBackedPropertyThroughOptimalAccessor() throws Exception {
        ReflectivePropertyAccessor reflectiveAccessor = new ReflectivePropertyAccessor();
        StandardEvaluationContext context = new StandardEvaluationContext();
        MethodBackedSample sample = new MethodBackedSample("from getter");

        PropertyAccessor accessor = reflectiveAccessor.createOptimalAccessor(context, sample, "name");

        assertThat(accessor)
                .isInstanceOf(CompilablePropertyAccessor.class);
        assertThat(accessor.canRead(context, sample, "name"))
                .isTrue();

        TypedValue value = accessor.read(context, sample, "name");

        assertThat(value.getValue())
                .isEqualTo("from getter");
    }

    @Test
    void readsFieldBackedPropertyThroughOptimalAccessor() throws Exception {
        ReflectivePropertyAccessor reflectiveAccessor = new ReflectivePropertyAccessor();
        StandardEvaluationContext context = new StandardEvaluationContext();
        FieldBackedSample sample = new FieldBackedSample();

        PropertyAccessor accessor = reflectiveAccessor.createOptimalAccessor(context, sample, "message");

        assertThat(accessor)
                .isInstanceOf(CompilablePropertyAccessor.class);
        assertThat(accessor.canRead(context, sample, "message"))
                .isTrue();

        TypedValue value = accessor.read(context, sample, "message");

        assertThat(value.getValue())
                .isEqualTo("from field");
    }

    public static final class MethodBackedSample {
        private final String name;

        public MethodBackedSample(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }
    }

    public static final class FieldBackedSample {
        public String message = "from field";
    }
}
