/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.junit.MockitoJUnitRunner;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TestMethodsFinderTest {
    static {
        System.setProperty("net.bytebuddy.experimental", "true");
    }

    @Test
    void reportsHelpfulErrorWhenMockitoRunnerReceivesClassWithoutJunitTestMethods() {
        assertThatThrownBy(() -> new MockitoJUnitRunner(NoTestMethodsFixture.class))
                .isInstanceOf(MockitoException.class)
                .hasMessageContaining("No tests found in NoTestMethodsFixture")
                .hasMessageContaining("Is the method annotated with @Test?")
                .hasMessageContaining("Is the method public?");
    }

    public static class NoTestMethodsFixture {
        public void helperMethod() {}
    }
}
