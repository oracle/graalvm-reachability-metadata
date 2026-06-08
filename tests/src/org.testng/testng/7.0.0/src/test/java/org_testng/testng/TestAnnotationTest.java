/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testng.testng;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.testng.IRetryAnalyzer;
import org.testng.ITestResult;
import org.testng.internal.annotations.TestAnnotation;

public class TestAnnotationTest {
    @Test
    void createsRetryAnalyzerFromConfiguredClass() {
        TestAnnotation annotation = new TestAnnotation();

        annotation.setRetryAnalyzer(AlwaysRetryAnalyzer.class);

        IRetryAnalyzer retryAnalyzer = annotation.getRetryAnalyzer();
        assertThat(retryAnalyzer).isInstanceOf(AlwaysRetryAnalyzer.class);
        assertThat(retryAnalyzer.retry(null)).isTrue();
    }

    public static class AlwaysRetryAnalyzer implements IRetryAnalyzer {
        @Override
        public boolean retry(ITestResult result) {
            return true;
        }
    }
}
