/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_jpamodelgen;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Supplier;

import org.graalvm.internal.tck.NativeImageSupport;
import org.hibernate.processor.validation.Mocker;
import org.junit.jupiter.api.Test;

public class MockerTest {
    @Test
    void nullaryCreatesMockWithDefaultAbstractMethodValues() {
        try {
            Supplier<NullaryContract> supplier = Mocker.nullary(NullaryContract.class);

            NullaryContract mock = supplier.get();

            assertThat(mock.name()).isEmpty();
            assertThat(mock.enabled()).isFalse();
            assertThat(mock.count()).isZero();
            assertThat(mock.total()).isZero();
            assertThat(mock.numbers()).isEmpty();
            assertThat(mock.labels()).isEmpty();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void variadicCreatesMockUsingMatchingConstructorArguments() {
        try {
            Mocker<VariadicContract> mocker = Mocker.variadic(VariadicContract.class);

            VariadicContract mock = mocker.make("customer", 42);

            assertThat(mock.entityName()).isEqualTo("customer");
            assertThat(mock.identifier()).isEqualTo(42);
            assertThat(mock.generatedString()).isEmpty();
            assertThat(mock.generatedBoolean()).isFalse();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public abstract static class NullaryContract {
        public abstract String name();

        public abstract boolean enabled();

        public abstract int count();

        public abstract long total();

        public abstract int[] numbers();

        public abstract String[] labels();
    }

    public abstract static class VariadicContract {
        private final String entityName;
        private final int identifier;

        public VariadicContract(String entityName, int identifier) {
            this.entityName = entityName;
            this.identifier = identifier;
        }

        public String entityName() {
            return entityName;
        }

        public int identifier() {
            return identifier;
        }

        public abstract String generatedString();

        public abstract boolean generatedBoolean();
    }
}
