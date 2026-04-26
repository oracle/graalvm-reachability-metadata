/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_netty.netty_common;

import java.util.concurrent.atomic.AtomicBoolean;

import io.netty.util.internal.ClassInitializerUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ClassInitializerUtilTest {
    @Test
    void tryLoadClassesInitializesEachProvidedClass() {
        Assertions.assertFalse(InitializationTracker.INITIALIZED.get(), "Expected the helper class to start uninitialized");

        ClassInitializerUtil.tryLoadClasses(ClassInitializerUtilTest.class, InitTrackingTarget.class);

        Assertions.assertTrue(InitializationTracker.INITIALIZED.get(), "Expected the helper class to be initialized");
    }

    private static final class InitializationTracker {
        private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

        private InitializationTracker() {
        }
    }

    private static final class InitTrackingTarget {
        static {
            InitializationTracker.INITIALIZED.set(true);
        }

        private InitTrackingTarget() {
        }
    }
}
