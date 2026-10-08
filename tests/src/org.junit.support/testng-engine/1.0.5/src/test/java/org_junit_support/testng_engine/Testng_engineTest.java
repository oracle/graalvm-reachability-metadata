/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_support.testng_engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;
import static org.junit.platform.launcher.EngineFilter.includeEngines;
import static org.junit.platform.launcher.TagFilter.excludeTags;
import static org.junit.platform.launcher.TagFilter.includeTags;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;

public class Testng_engineTest {
    @BeforeEach
    void resetFixtures() {
        LifecycleFixture.reset();
        ConfiguredFixture.reset();
        TagFixture.reset();
        RecordingListener.reset();
    }

    @Test
    void runsTestNgLifecycleDataProvidersAndDependencies() {
        TestExecutionSummary summary = execute(LifecycleFixture.class);

        assertThat(summary.getTestsFoundCount()).isEqualTo(5);
        assertThat(summary.getTestsSucceededCount()).isEqualTo(5);
        assertThat(summary.getTotalFailureCount()).isZero();
        assertThat(LifecycleFixture.beforeMethodInvocations).isEqualTo(5);
        assertThat(LifecycleFixture.values).containsExactlyInAnyOrder("alpha", "beta", "dependent", "first", "second");
    }

    @Test
    void executesOnlyTheSelectedTestNgMethod() {
        MethodSelectionFixture.executedMethods.clear();
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectMethod(MethodSelectionFixture.class, "selectedTest"))
                .filters(includeEngines("testng"))
                .build();

        TestExecutionSummary summary = execute(request);

        assertThat(summary.getTestsFoundCount()).isEqualTo(1);
        assertThat(summary.getTestsSucceededCount()).isEqualTo(1);
        assertThat(summary.getTotalFailureCount()).isZero();
        assertThat(MethodSelectionFixture.executedMethods).containsExactly("selectedTest");
    }

    @Test
    void appliesGroupsReturnValuesAndReflectivelyLoadedListeners() {
        LauncherDiscoveryRequest request = requestFor(ConfiguredFixture.class)
                .configurationParameter("testng.groups", "selected")
                .configurationParameter("testng.allowReturnValues", "true")
                .configurationParameter("testng.verbose", "1")
                .configurationParameter("testng.listeners", RecordingListener.class.getName())
                .build();

        TestExecutionSummary summary = execute(request);

        assertThat(summary.getTestsFoundCount()).isEqualTo(1);
        assertThat(summary.getTestsSucceededCount()).isEqualTo(1);
        assertThat(summary.getTotalFailureCount()).isZero();
        assertThat(ConfiguredFixture.executedMethods).containsExactly("selectedReturningTest");
        assertThat(RecordingListener.successfulMethods).containsExactly("selectedReturningTest");
    }

    @Test
    void exposesTestNgGroupsAsJUnitPlatformTags() {
        LauncherDiscoveryRequest request = requestFor(TagFixture.class)
                .filters(includeTags("fast"), excludeTags("quarantined"))
                .build();

        TestExecutionSummary summary = execute(request);

        assertThat(summary.getTestsFoundCount()).isEqualTo(1);
        assertThat(summary.getTestsSucceededCount()).isEqualTo(1);
        assertThat(summary.getTotalFailureCount()).isZero();
        assertThat(TagFixture.executedMethods).containsExactly("fastTest");
    }

    private static TestExecutionSummary execute(Class<?> testClass) {
        return execute(requestFor(testClass).build());
    }

    private static TestExecutionSummary execute(LauncherDiscoveryRequest request) {
        SummaryGeneratingListener summaryListener = new SummaryGeneratingListener();
        Launcher launcher = LauncherFactory.create();
        launcher.execute(request, summaryListener);
        return summaryListener.getSummary();
    }

    private static LauncherDiscoveryRequestBuilder requestFor(Class<?> testClass) {
        return LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(testClass))
                .filters(includeEngines("testng"));
    }

    public static class LifecycleFixture {
        static int beforeMethodInvocations;
        static final List<String> values = new ArrayList<>();

        static void reset() {
            beforeMethodInvocations = 0;
            values.clear();
        }

        @BeforeMethod
        public void beforeMethod() {
            beforeMethodInvocations++;
        }

        @DataProvider(name = "values")
        public Object[][] values() {
            return new Object[][] {{"alpha"}, {"beta"}};
        }

        @org.testng.annotations.Test(dataProvider = "values")
        public void dataDriven(String value) {
            values.add(value);
        }

        @org.testng.annotations.Test(dependsOnMethods = "dataDriven")
        public void dependent() {
            assertThat(values).contains("alpha", "beta");
            values.add("dependent");
        }

        @org.testng.annotations.Test(groups = "ordered")
        public void first() {
            values.add("first");
        }

        @org.testng.annotations.Test(dependsOnMethods = "first", groups = "ordered")
        public void second() {
            assertThat(values).contains("first");
            values.add("second");
        }
    }

    public static class MethodSelectionFixture {
        static final List<String> executedMethods = new ArrayList<>();

        @org.testng.annotations.Test
        public void selectedTest() {
            executedMethods.add("selectedTest");
        }

        @org.testng.annotations.Test
        public void otherTest() {
            executedMethods.add("otherTest");
        }
    }

    public static class ConfiguredFixture {
        static final List<String> executedMethods = new ArrayList<>();

        static void reset() {
            executedMethods.clear();
        }

        @org.testng.annotations.Test(groups = "selected")
        public String selectedReturningTest() {
            executedMethods.add("selectedReturningTest");
            return "result";
        }

        @org.testng.annotations.Test(groups = "excluded")
        public void excludedTest() {
            executedMethods.add("excludedTest");
        }
    }

    public static class TagFixture {
        static final List<String> executedMethods = new ArrayList<>();

        static void reset() {
            executedMethods.clear();
        }

        @org.testng.annotations.Test(groups = "fast")
        public void fastTest() {
            executedMethods.add("fastTest");
        }

        @org.testng.annotations.Test(groups = {"fast", "quarantined"})
        public void quarantinedFastTest() {
            executedMethods.add("quarantinedFastTest");
        }

        @org.testng.annotations.Test
        public void untaggedTest() {
            executedMethods.add("untaggedTest");
        }
    }

    public static class RecordingListener extends TestListenerAdapter {
        static final List<String> successfulMethods = new ArrayList<>();

        static void reset() {
            successfulMethods.clear();
        }

        @Override
        public void onTestSuccess(ITestResult result) {
            successfulMethods.add(result.getMethod().getMethodName());
        }
    }
}
