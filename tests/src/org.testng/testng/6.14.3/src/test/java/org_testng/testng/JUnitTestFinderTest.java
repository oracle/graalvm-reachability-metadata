/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import junit.framework.TestCase;
import org.junit.jupiter.api.Test;
import org.testng.junit.JUnitTestFinder;

public class JUnitTestFinderTest {
    @Test
    void recognizesJUnit3AndJUnit4TestClasses() {
        assertThat(JUnitTestFinder.isJUnitTest(JUnit3Sample.class)).isTrue();
        assertThat(JUnitTestFinder.isJUnitTest(JUnit4Sample.class)).isTrue();
    }

    @Test
    void rejectsPublicClassesWithoutJUnitTests() {
        assertThat(JUnitTestFinder.isJUnitTest(PlainPublicClass.class)).isFalse();
    }

    public static final class JUnit3Sample extends TestCase {
        public JUnit3Sample() {
        }

        public void testRecognizedByJUnit3() {
        }
    }

    public static final class JUnit4Sample {
        public JUnit4Sample() {
        }

        @org.junit.Test
        public void recognizedByJUnit4() {
        }
    }

    public static final class PlainPublicClass {
        public PlainPublicClass() {
        }
    }
}
