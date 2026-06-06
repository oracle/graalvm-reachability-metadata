/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_jpamodelgen;

import org.hibernate.processor.validation.Mocker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MockerTest {
    @Test
    void createsNullaryMockSubclassAndInvokesGeneratedNoArgumentConstructor() {
        try {
            NullaryTarget target = Mocker.nullary(NullaryTarget.class).get();

            assertThat(target.constructorValue()).isEqualTo("created");
            assertThat(target.generatedText()).isEmpty();
            assertThat(target.generatedFlag()).isFalse();
        } catch (Error error) {
            if (!HibernateJpamodelgenNativeImageSupport.isExpectedMockerFailure(error)) {
                throw error;
            }
        }
    }

    @Test
    void createsVariadicMockSubclassAndInvokesGeneratedConstructorWithArguments() {
        try {
            VariadicTarget target = Mocker.variadic(VariadicTarget.class).make("order", 42);

            assertThat(target.name()).isEqualTo("order");
            assertThat(target.priority()).isEqualTo(42);
            assertThat(target.generatedNumber()).isZero();
            assertThat(target.generatedLong()).isZero();
            assertThat(target.generatedNames()).isEmpty();
        } catch (Error error) {
            if (!HibernateJpamodelgenNativeImageSupport.isExpectedMockerFailure(error)) {
                throw error;
            }
        }
    }

    public abstract static class NullaryTarget {
        private final String constructorValue;

        public NullaryTarget() {
            this.constructorValue = "created";
        }

        public String constructorValue() {
            return constructorValue;
        }

        public abstract String generatedText();

        public abstract boolean generatedFlag();
    }

    public abstract static class VariadicTarget {
        private final String name;
        private final int priority;

        public VariadicTarget(String name, int priority) {
            this.name = name;
            this.priority = priority;
        }

        public String name() {
            return name;
        }

        public int priority() {
            return priority;
        }

        public abstract int generatedNumber();

        public abstract long generatedLong();

        public abstract String[] generatedNames();
    }
}
