/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_liquibase.liquibase_core;

import liquibase.database.Database;
import liquibase.exception.CustomPreconditionErrorException;
import liquibase.exception.CustomPreconditionFailedException;
import liquibase.exception.PreconditionFailedException;
import liquibase.precondition.CustomPrecondition;
import liquibase.precondition.CustomPreconditionWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class CustomPreconditionWrapperTest {

    @BeforeEach
    void resetState() {
        ExampleCustomPrecondition.checked = false;
        ExampleCustomPrecondition.configuredValue = null;
        NonCustomPrecondition.constructorInvocations = 0;
    }

    @Test
    void checkInstantiatesAndConfiguresCustomPrecondition() throws Exception {
        CustomPreconditionWrapper wrapper = new CustomPreconditionWrapper();
        wrapper.setClassName(ExampleCustomPrecondition.class.getName());
        wrapper.setParam("configuredValue", "expected-value");

        wrapper.check(null, null, null, null);

        assertThat(ExampleCustomPrecondition.checked).isTrue();
        assertThat(ExampleCustomPrecondition.configuredValue).isEqualTo("expected-value");
    }

    @Test
    void checkUsesDefaultClassLookupAfterScopedClassCastFailure() {
        CustomPreconditionWrapper wrapper = new CustomPreconditionWrapper();
        wrapper.setClassName(NonCustomPrecondition.class.getName());

        assertThatThrownBy(() -> wrapper.check(null, null, null, null))
                .isInstanceOf(PreconditionFailedException.class);
        assertThat(NonCustomPrecondition.constructorInvocations).isEqualTo(2);
    }

    public static class NonCustomPrecondition {
        private static int constructorInvocations;

        public NonCustomPrecondition() {
            constructorInvocations++;
        }
    }

    public static class ExampleCustomPrecondition implements CustomPrecondition {
        private static boolean checked;
        private static String configuredValue;

        public void setConfiguredValue(String configuredValue) {
            ExampleCustomPrecondition.configuredValue = configuredValue;
        }

        @Override
        public void check(Database database)
                throws CustomPreconditionFailedException, CustomPreconditionErrorException {
            checked = true;
        }
    }
}
