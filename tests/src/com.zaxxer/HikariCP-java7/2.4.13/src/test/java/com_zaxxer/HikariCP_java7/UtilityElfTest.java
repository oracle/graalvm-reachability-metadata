/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_zaxxer.HikariCP_java7;

import java.sql.Connection;

import com.zaxxer.hikari.util.UtilityElf;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilityElfTest {
    @Test
    public void createInstanceUsesNoArgConstructor() {
        NoArgConstructedDependency dependency = UtilityElf.createInstance(
                NoArgConstructedDependency.class.getName(),
                NoArgConstructedDependency.class
        );

        assertThat(dependency).isNotNull();
        assertThat(dependency.getValue()).isEqualTo("created");
    }

    @Test
    public void createInstanceUsesMatchingArgumentConstructor() {
        ArgumentConstructedDependency dependency = UtilityElf.createInstance(
                ArgumentConstructedDependency.class.getName(),
                ArgumentConstructedDependency.class,
                "jdbc:test"
        );

        assertThat(dependency).isNotNull();
        assertThat(dependency.getValue()).isEqualTo("jdbc:test");
    }

    @Test
    public void getTransactionIsolationResolvesNamedConstant() {
        assertThat(UtilityElf.getTransactionIsolation("TRANSACTION_SERIALIZABLE"))
                .isEqualTo(Connection.TRANSACTION_SERIALIZABLE);
    }

    public static final class NoArgConstructedDependency {
        private final String value;

        public NoArgConstructedDependency() {
            this.value = "created";
        }

        public String getValue() {
            return value;
        }
    }

    public static final class ArgumentConstructedDependency {
        private final String value;

        public ArgumentConstructedDependency(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
