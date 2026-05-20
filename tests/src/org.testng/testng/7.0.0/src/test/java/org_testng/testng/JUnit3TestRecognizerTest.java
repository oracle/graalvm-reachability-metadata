/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import junit.framework.TestSuite;
import org.junit.jupiter.api.Test;
import org.testng.junit.JUnit3TestRecognizer;

public class JUnit3TestRecognizerTest {
    @Test
    void recognizesJUnit3SuiteFactoryMethod() {
        JUnit3TestRecognizer recognizer = new JUnit3TestRecognizer();

        assertThat(recognizer.isTest(SuiteFactoryJUnit3Test.class)).isTrue();
    }

    public static final class SuiteFactoryJUnit3Test {
        public static junit.framework.Test suite() {
            TestSuite suite = new TestSuite();
            suite.addTest(new JUnit3TestCase("testFromSuite"));
            return suite;
        }
    }

    public static final class JUnit3TestCase extends junit.framework.TestCase {
        public JUnit3TestCase(String name) {
            super(name);
        }

        public void testFromSuite() {
        }
    }
}
