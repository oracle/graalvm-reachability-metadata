/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_configuration2.interpol;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.configuration2.interpol.ExprLookup;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ExprLookupInnerVariableTest {
    @Test
    void createsVariableValueFromPlainClassName() {
        final ExprLookup.Variable variable = new ExprLookup.Variable();
        variable.setName("builder");

        try {
            variable.setValue(StringBuilder.class.getName());

            assertThat(variable.getName()).isEqualTo("builder");
            assertThat(variable.getValue())
                    .isInstanceOf(StringBuilder.class)
                    .hasToString("");
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }
}
