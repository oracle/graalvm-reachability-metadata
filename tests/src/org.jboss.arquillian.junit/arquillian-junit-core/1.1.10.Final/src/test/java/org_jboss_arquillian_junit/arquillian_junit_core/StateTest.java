/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_junit.arquillian_junit_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.jdt.internal.junit.runner.RemoteTestRunner;
import org.jboss.arquillian.junit.State;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

@Order(1)
public class StateTest {
    private static final String ECLIPSE_RUNNER_CLASS_NAME = RemoteTestRunner.class.getName();

    @Test
    void eclipseDetectionInitializesStateThroughPublicApi() {
        assertThat(ECLIPSE_RUNNER_CLASS_NAME).isEqualTo("org.eclipse.jdt.internal.junit.runner.RemoteTestRunner");
        assertThat(State.isRunningInEclipse()).isTrue();
        assertThat(State.isNotRunningInEclipse()).isFalse();
    }

    @Test
    void caughtTestExceptionCanBeSetAndCleared() {
        Throwable failure = new IllegalStateException("managed test failure");

        State.caughtTestException(failure);
        try {
            assertThat(State.hasTestException()).isTrue();
            assertThat(State.getTestException()).isSameAs(failure);
        } finally {
            State.caughtTestException(null);
        }

        assertThat(State.hasTestException()).isFalse();
        assertThat(State.getTestException()).isNull();
    }

    @Test
    void caughtExceptionAfterJunitCanBeSetAndCleared() {
        Throwable failure = new AssertionError("after junit failure");

        State.caughtExceptionAfterJunit(failure);
        try {
            assertThat(State.caughtExceptionAfterJunit()).isSameAs(failure);
        } finally {
            State.caughtExceptionAfterJunit(null);
        }

        assertThat(State.caughtExceptionAfterJunit()).isNull();
    }
}
