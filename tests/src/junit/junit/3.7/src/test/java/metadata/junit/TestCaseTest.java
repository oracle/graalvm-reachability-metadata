/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package metadata.junit;

import junit.framework.TestCase;
import junit.framework.TestResult;

import org.junit.Ignore;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestCaseTest {
    @Test
    public void runBareInvokesNamedPublicTestMethod() throws Throwable {
        NamedMethodFixture testCase = new NamedMethodFixture("testMarksInvocation");

        testCase.runBare();

        assertEquals(1, testCase.invocationCount);
        assertEquals("setUp,testMarksInvocation,tearDown", testCase.events.toString());
    }

    @Test
    public void runRecordsSuccessfulNamedPublicTestMethod() {
        NamedMethodFixture testCase = new NamedMethodFixture("testMarksInvocation");
        TestResult result = new TestResult();

        testCase.run(result);

        assertEquals(1, result.runCount());
        assertEquals(0, result.errorCount());
        assertEquals(0, result.failureCount());
        assertEquals(1, testCase.invocationCount);
    }

    @Ignore
    public static class NamedMethodFixture extends TestCase {
        private final StringBuilder events = new StringBuilder();
        private int invocationCount;

        public NamedMethodFixture(String name) {
            super(name);
        }

        @Override
        protected void setUp() {
            record("setUp");
        }

        public void testMarksInvocation() {
            invocationCount++;
            record("testMarksInvocation");
        }

        @Override
        protected void tearDown() {
            record("tearDown");
        }

        private void record(String event) {
            if (events.length() > 0) {
                events.append(',');
            }
            events.append(event);
        }
    }
}
