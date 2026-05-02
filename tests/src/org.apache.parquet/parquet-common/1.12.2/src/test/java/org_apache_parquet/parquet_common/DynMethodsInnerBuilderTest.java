/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_common;

import org.apache.parquet.util.DynMethods;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DynMethodsInnerBuilderTest {
    @Test
    public void findsPublicMethodByClassName() {
        DynMethods.UnboundMethod method = new DynMethods.Builder("publicGreeting")
                .impl(MethodTargets.class.getName(), String.class)
                .build();

        assertThat(method.isStatic()).isFalse();
        assertThat(method.toString()).contains("publicGreeting", MethodTargets.class.getName());
    }

    @Test
    public void findsHiddenMethodByClassName() {
        DynMethods.UnboundMethod method = new DynMethods.Builder("secretGreeting")
                .hiddenImpl(MethodTargets.class.getName(), String.class)
                .build();

        assertThat(method.isStatic()).isFalse();
        assertThat(method.toString()).contains("secretGreeting", MethodTargets.class.getName());
    }

    public static final class MethodTargets {
        public String publicGreeting(String name) {
            return "hello " + name;
        }

        private String secretGreeting(String name) {
            return "secret " + name;
        }
    }
}
