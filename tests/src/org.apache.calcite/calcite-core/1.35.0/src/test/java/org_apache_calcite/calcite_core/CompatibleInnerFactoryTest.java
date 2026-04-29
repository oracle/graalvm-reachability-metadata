/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.apache.calcite.util.Compatible;
import org.junit.jupiter.api.Test;

public class CompatibleInnerFactoryTest {
    @Test
    void lookupPrivateProvidesAccessToPrivateMembers() throws Throwable {
        MethodHandles.Lookup lookup = Compatible.INSTANCE.lookupPrivate(SecretOperations.class);
        MethodHandle handle = lookup.findStatic(
                SecretOperations.class,
                "describe",
                MethodType.methodType(String.class, String.class));

        String description = (String) handle.invoke("calcite");

        assertThat(description).isEqualTo("hidden-calcite");
    }

    private static final class SecretOperations {
        private static String describe(String value) {
            return "hidden-" + value;
        }
    }
}
