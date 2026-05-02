/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.junit.DoNotRunWith;
import com.google.gwt.junit.JUnitFatalLaunchException;
import com.google.gwt.junit.JUnitShell;
import com.google.gwt.junit.Platform;
import com.google.gwt.junit.client.GWTTestCase;
import com.google.gwt.junit.client.impl.JUnitHost.TestInfo;

import junit.framework.TestFailure;
import junit.framework.TestResult;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.util.Enumeration;

public class JUnitShellTest {
    private static final String GWT_ARGS_PROPERTY = "gwt.args";
    private static final String GWT_ARGS = "-runStyle Manual -devMode -testBeginTimeout 0 -testMethodTimeout 0";
    private static final String CORE_MODULE = "com.google.gwt.core.Core";

    @Test
    @DoNotRunWith({Platform.Devel, Platform.Prod})
    public void startsShellAndReadsBannedPlatformAnnotations() {
        withGwtArgs(() -> {
            TestResult result = runShellBackedTest();
            if (hasSupportedNativeImageError(result)) {
                return;
            }

            assertThat(result.runCount()).isEqualTo(1);
            assertThat(result.errorCount()).isEqualTo(1);
            assertThat(firstError(result))
                    .isInstanceOf(JUnitFatalLaunchException.class)
                    .hasMessageContaining("not found in module");

            TestInfo testInfo = new TestInfo(
                    CORE_MODULE,
                    JUnitShellTest.class.getName(),
                    "startsShellAndReadsBannedPlatformAnnotations"
            );

            assertMustNotExecute(testInfo);
        });
    }

    private static void withGwtArgs(Runnable test) {
        String previousArgs = System.getProperty(GWT_ARGS_PROPERTY);
        System.setProperty(GWT_ARGS_PROPERTY, GWT_ARGS);
        try {
            test.run();
        } finally {
            if (previousArgs == null) {
                System.clearProperty(GWT_ARGS_PROPERTY);
            } else {
                System.setProperty(GWT_ARGS_PROPERTY, previousArgs);
            }
        }
    }

    private static TestResult runShellBackedTest() {
        TestResult result = new TestResult();
        ShellBackedGwtTest test = new ShellBackedGwtTest();
        test.setName("testMethodNotInModule");
        test.run(result);
        return result;
    }

    private static void assertMustNotExecute(TestInfo testInfo) {
        try {
            assertThat(JUnitShell.mustNotExecuteTest(testInfo)).isTrue();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static boolean hasSupportedNativeImageError(TestResult result) {
        Enumeration<TestFailure> errors = result.errors();
        while (errors.hasMoreElements()) {
            Throwable error = errors.nextElement().thrownException();
            if (error instanceof Error) {
                Error nativeImageError = (Error) error;
                if (!NativeImageSupport.isUnsupportedFeatureError(nativeImageError)) {
                    throw nativeImageError;
                }
                return true;
            }
        }
        return false;
    }

    private static Throwable firstError(TestResult result) {
        Enumeration<TestFailure> errors = result.errors();
        assertThat(errors.hasMoreElements()).isTrue();
        return errors.nextElement().thrownException();
    }

    private static final class ShellBackedGwtTest extends GWTTestCase {
        @Override
        public String getModuleName() {
            return CORE_MODULE;
        }

        public void testMethodNotInModule() {
        }
    }
}
