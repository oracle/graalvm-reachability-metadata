/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_expression;

import org.junit.jupiter.api.Test;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectivePropertyAccessorTest {
    @Test
    void readsAndWritesPropertiesThroughGetterAndSetterMethods() throws Exception {
        ReflectivePropertyAccessor accessor = new ReflectivePropertyAccessor();
        StandardEvaluationContext context = new StandardEvaluationContext();
        MethodBackedSample sample = new MethodBackedSample("initial");

        assertThat(accessor.canRead(context, sample, "name"))
                .isTrue();
        TypedValue initialValue = accessor.read(context, sample, "name");
        assertThat(initialValue.getValue())
                .isEqualTo("initial");

        assertThat(accessor.canWrite(context, sample, "name"))
                .isTrue();
        accessor.write(context, sample, "name", "updated");

        assertThat(sample.getName())
                .isEqualTo("updated");
        assertThat(accessor.read(context, sample, "name").getValue())
                .isEqualTo("updated");
    }

    @Test
    void readsAndWritesPublicFieldsWhenAccessorMethodsAreAbsent() throws Exception {
        ReflectivePropertyAccessor accessor = new ReflectivePropertyAccessor();
        StandardEvaluationContext context = new StandardEvaluationContext();
        FieldBackedSample sample = new FieldBackedSample();

        assertThat(accessor.canRead(context, sample, "message"))
                .isTrue();
        TypedValue initialValue = accessor.read(context, sample, "message");
        assertThat(initialValue.getValue())
                .isEqualTo("from field");

        assertThat(accessor.canWrite(context, sample, "message"))
                .isTrue();
        accessor.write(context, sample, "message", "changed through field");

        assertThat(sample.message)
                .isEqualTo("changed through field");
        assertThat(accessor.read(context, sample, "message").getValue())
                .isEqualTo("changed through field");
    }

    public static final class MethodBackedSample {
        private String name;

        public MethodBackedSample(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static final class FieldBackedSample {
        public String message = "from field";
    }
}
