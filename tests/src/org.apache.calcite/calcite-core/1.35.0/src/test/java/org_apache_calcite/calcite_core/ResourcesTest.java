/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.runtime.Resources;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThatCode;

public class ResourcesTest {
    @Test
    public void validatesResourceMethodsThroughProxyInvocation() {
        TestResourceObject resourceObject = new TestResourceObject();
        EnumSet<Resources.Validation> validations = EnumSet.of(
                Resources.Validation.AT_LEAST_ONE,
                Resources.Validation.MESSAGE_SPECIFIED,
                Resources.Validation.EVEN_QUOTES,
                Resources.Validation.ARGUMENT_MATCH);

        assertThatCode(() -> Resources.validate(resourceObject, validations))
                .doesNotThrowAnyException();
    }

    public static class TestResourceObject {
        private final TestMessages messages = Resources.create(TestMessages.class);

        public Resources.Inst resourceValue(String name, int value) {
            return messages.resourceValue(name, value);
        }
    }

    public interface TestMessages {
        @Resources.BaseMessage("Resource {0} has value {1,number,#}")
        Resources.Inst resourceValue(String name, int value);
    }
}
