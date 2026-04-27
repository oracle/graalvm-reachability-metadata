/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kafka.kafka_clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.common.utils.Utils;
import org.junit.jupiter.api.Test;

public class UtilsTest {

    @Test
    void newInstanceLoadsNamedClassAndInvokesPublicNoArgConstructor() throws ClassNotFoundException {
        ConstructedRunnable target = Utils.newInstance(ConstructedRunnable.class.getName(), ConstructedRunnable.class);

        assertThat(target).isNotNull();
        assertThat(target.constructed()).isTrue();
    }

    @Test
    void newParameterizedInstanceUsesMatchingPublicConstructor() throws ClassNotFoundException {
        ParameterizedTarget target = Utils.newParameterizedInstance(
            ParameterizedTarget.class.getName(),
            String.class,
            "kafka",
            Integer.class,
            42
        );

        assertThat(target.value()).isEqualTo("kafka-42");
    }

    @Test
    void ensureConcreteSubclassSuggestsConcretePublicChildren() {
        assertThatThrownBy(() -> Utils.ensureConcreteSubclass(Contract.class, AbstractContract.class))
            .isInstanceOf(ConfigException.class)
            .hasMessageContaining("This class is abstract and cannot be created.")
            .hasMessageContaining(AbstractContract.ConcreteChild.class.getName());
    }

    public interface Contract {
        String value();
    }

    public static class ConstructedRunnable implements Runnable {
        private final boolean constructed;

        public ConstructedRunnable() {
            this.constructed = true;
        }

        public boolean constructed() {
            return this.constructed;
        }

        @Override
        public void run() {
        }
    }

    public static class ParameterizedTarget {
        private final String value;

        public ParameterizedTarget(String prefix, Integer number) {
            this.value = prefix + "-" + number;
        }

        public String value() {
            return this.value;
        }
    }

    public abstract static class AbstractContract implements Contract {
        public static class ConcreteChild extends AbstractContract {
            @Override
            public String value() {
                return "child";
            }
        }
    }
}
