/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.runner.Description;
import org.testng.junit.JUnit4TestClass;
import org.testng.junit.JUnit4TestMethod;

public class JUnit4TestMethodTest {
    @Test
    void resolvesJUnit4DescriptionMethod() {
        Description description = Description.createTestDescription(JUnit4StyleTestCase.class, "sampleTest[0]");
        JUnit4TestClass testClass = new JUnit4TestClass(description);

        JUnit4TestMethod testMethod = new JUnit4TestMethod(testClass, description);

        assertThat(testMethod.getMethodName()).isEqualTo("sampleTest[0]");
        assertThat(testMethod.getConstructorOrMethod().getMethod().getName()).isEqualTo("sampleTest");
        assertThat(testMethod.isTest()).isTrue();
        assertThat(testClass.getTestMethods()).containsExactly(testMethod);
    }

    public static final class JUnit4StyleTestCase {
        public void sampleTest() {
        }
    }
}
