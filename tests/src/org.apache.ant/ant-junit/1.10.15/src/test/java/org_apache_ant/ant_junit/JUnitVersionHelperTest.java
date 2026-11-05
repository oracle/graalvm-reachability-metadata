/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant_junit;

import static org.assertj.core.api.Assertions.assertThat;

import junit.extensions.TestDecorator;
import junit.framework.JUnit4TestAdapter;
import junit.framework.JUnit4TestAdapterCache;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.tools.ant.taskdefs.optional.junit.JUnitVersionHelper;
import org.junit.jupiter.api.Test;
import org.junit.runner.Description;

public class JUnitVersionHelperTest {
    @Test
    void readsNamesFromTestCasesAndCustomTestImplementations() {
        TestCase testCase = new JUnitTaskTest.ConsumerCase();
        assertThat(JUnitVersionHelper.getTestCaseName(testCase)).isEqualTo("testConsumerCheck");
        assertThat(JUnitVersionHelper.getTestCaseClassName(testCase)).isEqualTo(testCase.getClass().getName());
        assertThat(JUnitVersionHelper.getTestCaseName(new TestSuite("named suite"))).isEqualTo("named suite");
        assertThat(JUnitVersionHelper.getTestCaseName(new LegacyNamedTest())).isEqualTo("legacy suite");
        assertThat(JUnitVersionHelper.getTestCaseName(null)).isEqualTo("unknown");
    }

    @Test
    void extractsNamesAndIgnoreReasonsFromJUnit4AdapterTests() {
        JUnit4TestAdapter adapter = new JUnit4TestAdapter(JUnit4Scenario.class);
        assertThat(adapter.getTests()).hasSize(1);
        var active = adapter.getTests().get(0);
        assertThat(JUnitVersionHelper.getTestCaseName(active)).isEqualTo("addsNumbers");
        assertThat(JUnitVersionHelper.getTestCaseClassName(active)).isEqualTo(JUnit4Scenario.class.getName());
        assertThat(JUnitVersionHelper.getIgnoreMessage(active)).isNull();

        var ignored = JUnit4TestAdapterCache.getDefault().asTest(
                Description.createTestDescription(JUnit4Scenario.class, "optionalIntegration"));
        assertThat(JUnitVersionHelper.getTestCaseName(ignored)).isEqualTo("optionalIntegration");
        assertThat(JUnitVersionHelper.getTestCaseClassName(ignored)).isEqualTo(JUnit4Scenario.class.getName());
        assertThat(JUnitVersionHelper.getIgnoreMessage(ignored)).isEqualTo("awaiting an optional integration service");
    }

    public static class LegacyNamedTest extends TestDecorator {
        public LegacyNamedTest() {
            super(new TestSuite());
        }

        public String name() {
            return "legacy suite";
        }
    }
}
