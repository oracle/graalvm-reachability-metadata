/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.modelmapper.internal.bytebuddy.dynamic.loading;

import java.lang.reflect.Field;
import java.security.Permission;
import java.security.ProtectionDomain;
import java.util.Map;

import org.modelmapper.internal.bytebuddy.dynamic.loading.ClassInjector.UsingUnsafe.Dispatcher;

import sun.misc.Unsafe;

public final class UsingUnsafeAccess {
    private UsingUnsafeAccess() {
    }

    public static Map<String, Class<?>> injectWithThrowingDispatcher(
        ClassLoader classLoader,
        Throwable throwable,
        Map<? extends String, byte[]> types) {
        return new TestableUsingUnsafe(classLoader, new ThrowingInitializable(throwable))
            .injectRaw(types);
    }

    public static boolean initializeEnabledDispatcherWithSecurityManager() throws Exception {
        Field system = ClassInjector.UsingUnsafe.class.getDeclaredField("SYSTEM");
        Unsafe unsafe = unsafe();
        Object fieldBase = unsafe.staticFieldBase(system);
        long fieldOffset = unsafe.staticFieldOffset(system);
        Object original = unsafe.getObject(fieldBase, fieldOffset);
        RecordingSecurityManager securityManager = new RecordingSecurityManager();
        TestableEnabledDispatcher dispatcher = new TestableEnabledDispatcher();
        try {
            unsafe.putObject(fieldBase, fieldOffset, new PermissionCheckingSystem(securityManager));
            return dispatcher.initialize() == dispatcher && securityManager.hasCheckedSuppressAccessChecks();
        } finally {
            unsafe.putObject(fieldBase, fieldOffset, original == null ? new NoSecurityManagerSystem() : original);
        }
    }

    private static Unsafe unsafe() throws Exception {
        Field unsafe = Unsafe.class.getDeclaredField("theUnsafe");
        unsafe.setAccessible(true);
        return (Unsafe) unsafe.get(null);
    }

    private static final class TestableUsingUnsafe extends ClassInjector.UsingUnsafe {
        TestableUsingUnsafe(ClassLoader classLoader, Dispatcher.Initializable dispatcher) {
            super(classLoader, protectionDomain(), dispatcher);
        }

        private static ProtectionDomain protectionDomain() {
            return UsingUnsafeAccess.class.getProtectionDomain();
        }
    }

    private static final class TestableEnabledDispatcher extends Dispatcher.Enabled {
        private TestableEnabledDispatcher() {
            super(null, null);
        }
    }

    private static final class PermissionCheckingSystem implements ClassInjector.UsingUnsafe.System {
        private final RecordingSecurityManager securityManager;

        private PermissionCheckingSystem(RecordingSecurityManager securityManager) {
            this.securityManager = securityManager;
        }

        @Override
        public Object getSecurityManager() {
            return securityManager;
        }
    }

    private static final class NoSecurityManagerSystem implements ClassInjector.UsingUnsafe.System {
        @Override
        public Object getSecurityManager() {
            return null;
        }
    }

    @SuppressWarnings("removal")
    private static final class RecordingSecurityManager extends SecurityManager {
        private boolean checkedSuppressAccessChecks;

        private boolean hasCheckedSuppressAccessChecks() {
            return checkedSuppressAccessChecks;
        }

        @Override
        public void checkPermission(Permission permission) {
            if (ClassInjector.SUPPRESS_ACCESS_CHECKS.equals(permission)) {
                checkedSuppressAccessChecks = true;
            }
        }
    }

    private static final class ThrowingInitializable
        implements Dispatcher.Initializable, Dispatcher {
        private final Throwable throwable;

        ThrowingInitializable(Throwable throwable) {
            this.throwable = throwable;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public Dispatcher initialize() {
            return this;
        }

        @Override
        public Class<?> defineClass(
            ClassLoader classLoader,
            String name,
            byte[] binaryRepresentation,
            ProtectionDomain protectionDomain) {
            if (throwable instanceof RuntimeException) {
                throw (RuntimeException) throwable;
            }
            if (throwable instanceof Error) {
                throw (Error) throwable;
            }
            throw new AssertionError(throwable);
        }
    }
}
