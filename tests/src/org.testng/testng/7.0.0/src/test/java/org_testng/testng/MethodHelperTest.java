/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.testng.TestNG;
import org.testng.TestNGException;

public class MethodHelperTest {
    @Test
    void reportsUnannotatedDependencyDiscoveredByNameLookup() {
        TestNG testNg = new TestNG();
        testNg.setUseDefaultListeners(false);
        testNg.setVerbose(0);
        testNg.setTestClasses(new Class<?>[] {MethodHelperTest.class});

        assertThatThrownBy(testNg::run)
                .isInstanceOf(TestNGException.class)
                .hasMessageContaining("dependentTestMethod")
                .hasMessageContaining("unannotatedDependencyMethod")
                .hasMessageContaining("not annotated with @Test or not included");
    }

    @org.testng.annotations.Test(dependsOnMethods = "unannotatedDependencyMethod")
    public void dependentTestMethod() {
        throw new AssertionError("Dependency validation should fail before this method runs");
    }

    public void unannotatedDependencyMethod() {
    }
}
