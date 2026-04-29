/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.EnumSet;
import org.apache.calcite.runtime.Resources;
import org.junit.jupiter.api.Test;

public class ResourcesTest {
    @Test
    void validatesGeneratedResourceProxyMethods() {
        ValidationMessages messages = Resources.create(ValidationMessages.class);
        EnumSet<Resources.Validation> validations = EnumSet.of(
                Resources.Validation.AT_LEAST_ONE,
                Resources.Validation.MESSAGE_SPECIFIED,
                Resources.Validation.EVEN_QUOTES,
                Resources.Validation.ARGUMENT_MATCH);

        assertThatCode(() -> Resources.validate(messages, validations)).doesNotThrowAnyException();
    }

    public interface ValidationMessages {
        @Resources.BaseMessage("Planner {0} selected {1,number,#} rules")
        Resources.Inst selectedRules(String plannerName, int ruleCount);
    }
}
