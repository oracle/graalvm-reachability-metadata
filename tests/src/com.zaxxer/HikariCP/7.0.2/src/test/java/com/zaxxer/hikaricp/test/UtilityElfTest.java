/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com.zaxxer.hikaricp.test;

import com.zaxxer.hikari.util.FastList;
import com.zaxxer.hikari.util.UtilityElf;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilityElfTest {
    @Test
    void createsInstancesAndHandlesCommonHelpers() {
        NoArgConstructedDependency noArgDependency = UtilityElf.createInstance(
                NoArgConstructedDependency.class.getName(),
                NoArgConstructedDependency.class);
        ArgumentConstructedDependency argumentDependency = UtilityElf.createInstance(
                ArgumentConstructedDependency.class.getName(),
                ArgumentConstructedDependency.class,
                "configured");

        assertThat(noArgDependency.getValue()).isEqualTo("created");
        assertThat(argumentDependency.getValue()).isEqualTo("configured");
        assertThat(UtilityElf.safeIsAssignableFrom(new FastList<>(String.class), "java.util.List")).isTrue();
        assertThat(UtilityElf.safeIsAssignableFrom(new Object(), "example.missing.DoesNotExist")).isFalse();
        assertThat(UtilityElf.getNullIfEmpty("   ")).isNull();
        assertThat(UtilityElf.getNullIfEmpty("hikari")).isEqualTo("hikari");
        assertThat(UtilityElf.maskPasswordInJdbcUrl("jdbc:test://host/db?password=secret&user=duke"))
                .contains("password=<masked>");
    }

    public static final class NoArgConstructedDependency {
        private final String value;

        public NoArgConstructedDependency() {
            this.value = "created";
        }

        String getValue() {
            return value;
        }
    }

    public static final class ArgumentConstructedDependency {
        private final String value;

        public ArgumentConstructedDependency(String value) {
            this.value = value;
        }

        String getValue() {
            return value;
        }
    }
}
