/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package netty;

import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.util.internal.ClassInitializerUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ClassInitializerUtilTest {
    @Test
    void preloadingRunsStaticInitializer() {
        Assertions.assertFalse(Tracker.initialized.get());
        ClassInitializerUtil.tryLoadClasses(ClassInitializerUtilTest.class, PreloadedService.class);
        Assertions.assertTrue(Tracker.initialized.get());
    }

    private static final class Tracker {
        private static final AtomicBoolean initialized = new AtomicBoolean();
    }

    public static final class PreloadedService {
        static {
            Tracker.initialized.set(true);
        }
    }
}
