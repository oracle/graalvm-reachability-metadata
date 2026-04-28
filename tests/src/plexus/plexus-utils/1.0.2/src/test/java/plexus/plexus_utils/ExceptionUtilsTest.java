/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package plexus.plexus_utils;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.rmi.RemoteException;

import org.codehaus.plexus.util.ExceptionUtils;
import org.junit.jupiter.api.Test;

public class ExceptionUtilsTest {
    @Test
    void getCauseUsesConfiguredCauseMethod() {
        Throwable rootCause = new IllegalArgumentException("root cause");
        RemoteException wrapper = new RemoteException("wrapper", rootCause);

        Throwable cause = ExceptionUtils.getCause(wrapper, new String[] { "getCause" });

        assertSame(rootCause, cause);
    }

    @Test
    void getCauseFallsBackToLegacyDetailField() {
        Throwable rootCause = new IllegalStateException("legacy detail");
        RemoteException wrapper = new RemoteException("wrapper", rootCause);

        Throwable cause = ExceptionUtils.getCause(wrapper, new String[0]);

        assertSame(rootCause, cause);
    }

    @Test
    void isNestedThrowableDetectsCauseMethod() {
        assertTrue(ExceptionUtils.isNestedThrowable(new Throwable("has inherited getCause")));
    }

    @Test
    void isNestedThrowableDetectsLegacyDetailField() {
        String[] originalCauseMethodNames = ExceptionUtilsAccess.getCauseMethodNames();
        try {
            ExceptionUtilsAccess.setCauseMethodNames(new String[0]);
            RemoteException wrapper = new RemoteException("wrapper", new IllegalStateException("legacy detail"));

            assertTrue(ExceptionUtils.isNestedThrowable(wrapper));
        } finally {
            ExceptionUtilsAccess.setCauseMethodNames(originalCauseMethodNames);
        }
    }

    private static class ExceptionUtilsAccess extends ExceptionUtils {
        static String[] getCauseMethodNames() {
            return CAUSE_METHOD_NAMES.clone();
        }

        static void setCauseMethodNames(String[] causeMethodNames) {
            CAUSE_METHOD_NAMES = causeMethodNames.clone();
        }
    }
}
