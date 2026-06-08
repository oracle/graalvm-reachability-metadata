/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testng.internal.TestResult;

public class TestResultTest {
    @Test
    void clonesCloneableParametersUsingDeclaredCloneMethod() {
        TestResult result = new TestResult();
        CloneableParameter parameter = new CloneableParameter("original");

        result.setParameters(new Object[] {parameter});

        Object copiedParameter = result.getParameters()[0];
        assertThat(copiedParameter).isInstanceOf(CloneableParameter.class);
        assertThat(copiedParameter).isNotSameAs(parameter);
        assertThat(((CloneableParameter) copiedParameter).value).isEqualTo("original");
    }

    public static final class CloneableParameter implements Cloneable {
        private final String value;

        public CloneableParameter(String value) {
            this.value = value;
        }

        @Override
        public CloneableParameter clone() {
            return new CloneableParameter(value);
        }
    }
}
