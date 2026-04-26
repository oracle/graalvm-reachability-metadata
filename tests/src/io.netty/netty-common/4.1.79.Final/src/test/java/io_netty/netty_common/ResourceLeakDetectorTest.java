/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import io.netty.util.ResourceLeakDetector;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ResourceLeakDetectorTest {
    @Test
    void addExclusionsAcceptsDeclaredMethodNames() {
        Assertions.assertDoesNotThrow(
                () -> ResourceLeakDetector.addExclusions(ExclusionTarget.class, "recordLeak", "clearLeak")
        );
    }

    @Test
    void addExclusionsRejectsUnknownMethodNames() {
        IllegalArgumentException exception = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> ResourceLeakDetector.addExclusions(ExclusionTarget.class, "missingMethod")
        );

        Assertions.assertTrue(exception.getMessage().contains("missingMethod"));
        Assertions.assertTrue(exception.getMessage().contains(ExclusionTarget.class.getName()));
    }

    public static final class ExclusionTarget {
        public void recordLeak() {
        }

        public void clearLeak() {
        }
    }
}
