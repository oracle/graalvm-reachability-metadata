/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.HikariCP;

import com.zaxxer.hikari.util.UtilityElf;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilityElfTest {

    @Test
    void safeIsAssignableFromLoadsClassByName() {
        assertThat(UtilityElf.safeIsAssignableFrom(new SampleImplementation(), SampleContract.class.getName())).isTrue();
        assertThat(UtilityElf.safeIsAssignableFrom(new SampleImplementation(), "com.example.DoesNotExist")).isFalse();
    }

    @Test
    void createInstanceUsesDeclaredNoArgConstructor() {
        SampleContract instance = UtilityElf.createInstance(NoArgImplementation.class.getName(), SampleContract.class);

        assertThat(instance).isInstanceOf(NoArgImplementation.class);
        assertThat(instance.value()).isEqualTo("no-arg");
    }

    @Test
    void createInstanceUsesPublicConstructorMatchingArguments() {
        SampleContract instance = UtilityElf.createInstance(StringArgImplementation.class.getName(), SampleContract.class, "configured");

        assertThat(instance).isInstanceOf(StringArgImplementation.class);
        assertThat(instance.value()).isEqualTo("configured");
    }

    public interface SampleContract {
        String value();
    }

    public static final class SampleImplementation implements SampleContract {

        @Override
        public String value() {
            return "sample";
        }
    }

    public static final class NoArgImplementation implements SampleContract {

        public NoArgImplementation() {
        }

        @Override
        public String value() {
            return "no-arg";
        }
    }

    public static final class StringArgImplementation implements SampleContract {
        private final String value;

        public StringArgImplementation(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }
}
