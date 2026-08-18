/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import org.assertj.core.api.Assertions;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

/// Expands AssertJ stack-trace rendering so native-image test failures show the
/// full `Caused by:` chain instead of AssertJ's default 3-element truncation.
///
/// Registered as a JUnit `TestExecutionListener` service so it applies to both
/// the JVM and native test runs without per-test setup. The default truncation
/// hides the root cause of `ExceptionInInitializerError`-style native failures,
/// which forces the metadata-fix tooling to rediscover it from scratch.
public final class FullStackTraceTestExecutionListener implements TestExecutionListener {

    private static final int MAX_STACK_TRACE_ELEMENTS = 1000;

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        Assertions.setMaxStackTraceElementsDisplayed(MAX_STACK_TRACE_ELEMENTS);
    }
}
