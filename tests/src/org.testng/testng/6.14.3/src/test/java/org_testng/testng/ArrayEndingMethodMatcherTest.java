/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.testng.TestNG;

public class ArrayEndingMethodMatcherTest {
    @Test
    void groupsTrailingDataProviderArgumentsForVarargsTestMethod() {
        VarargsDataProviderTestCase.reset();

        TestNG testNg = new TestNG();
        testNg.setUseDefaultListeners(false);
        testNg.setVerbose(0);
        testNg.setTestClasses(new Class<?>[] {VarargsDataProviderTestCase.class});
        testNg.run();

        assertThat(testNg.hasFailure()).isFalse();
        assertThat(VarargsDataProviderTestCase.testMethodCalls.get()).isEqualTo(1);
        assertThat(VarargsDataProviderTestCase.receivedLabel.get()).isEqualTo("provided-values");
        assertThat(VarargsDataProviderTestCase.receivedValues.get())
                .containsExactly("alpha", "beta", "gamma");
    }

    public static final class VarargsDataProviderTestCase {
        private static final AtomicInteger testMethodCalls = new AtomicInteger();
        private static final AtomicReference<String> receivedLabel = new AtomicReference<>();
        private static final AtomicReference<String[]> receivedValues = new AtomicReference<>();

        public VarargsDataProviderTestCase() {
        }

        private static void reset() {
            testMethodCalls.set(0);
            receivedLabel.set(null);
            receivedValues.set(new String[0]);
        }

        @org.testng.annotations.DataProvider(name = "varargValues")
        public Object[][] varargValues() {
            return new Object[][] {
                    {"provided-values", "alpha", "beta", "gamma"}
            };
        }

        @org.testng.annotations.Test(dataProvider = "varargValues")
        public void consumesVarargs(String label, String... values) {
            testMethodCalls.incrementAndGet();
            receivedLabel.set(label);
            receivedValues.set(Arrays.copyOf(values, values.length));
        }
    }
}
