/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.modelmapper.internal.bytebuddy.dynamic.loading;

import java.lang.reflect.Field;
import java.net.URL;
import java.security.Permission;
import java.security.ProtectionDomain;

import org.modelmapper.internal.bytebuddy.dynamic.loading.ClassInjector.UsingReflection.Dispatcher;
import org.modelmapper.internal.bytebuddy.dynamic.loading.ClassInjector.UsingReflection.Dispatcher.Direct;
import org.modelmapper.internal.bytebuddy.dynamic.loading.ClassInjector.UsingReflection.Dispatcher.Initializable;
import org.modelmapper.internal.bytebuddy.dynamic.loading.ClassInjector.UsingReflection.Dispatcher.Unavailable;
import org.modelmapper.internal.bytebuddy.dynamic.loading.ClassInjector.UsingReflection.Dispatcher.UsingUnsafeOverride;

import sun.misc.Unsafe;

public final class UnsafeOverrideDispatcherAccess {
    private UnsafeOverrideDispatcherAccess() {
    }

    public static Operations create() throws Exception {
        Initializable initializable = UnsafeOverrideAccess.makeDispatcher();
        if (!initializable.isAvailable()) {
            throw new IllegalStateException("Unsafe override dispatcher is unavailable");
        }
        return new Operations(initializable.initialize());
    }

    public static Operations createDirect() throws Exception {
        Initializable initializable = DirectAccess.makeDispatcher();
        if (!initializable.isAvailable()) {
            throw new IllegalStateException("Direct reflection dispatcher is unavailable");
        }
        return new Operations(initializeWithSecurityManager(initializable));
    }

    public static Operations createUnavailable() {
        return new Operations(new UnavailableAccess());
    }

    public static void exerciseSecurityManagerPermissionCheck() throws Exception {
        initializeWithSecurityManager(UnsafeOverrideAccess.makeDispatcher());
    }

    private static Dispatcher initializeWithSecurityManager(Initializable initializable) throws Exception {
        Field system = ClassInjector.UsingReflection.class.getDeclaredField("SYSTEM");
        Unsafe unsafe = unsafe();
        Object fieldBase = unsafe.staticFieldBase(system);
        long fieldOffset = unsafe.staticFieldOffset(system);
        Object original = unsafe.getObject(fieldBase, fieldOffset);
        try {
            unsafe.putObject(fieldBase, fieldOffset, new PermissionCheckingSystem());
            return initializable.initialize();
        } finally {
            unsafe.putObject(
                fieldBase,
                fieldOffset,
                original == null ? new NoSecurityManagerSystem() : original);
        }
    }

    public static Reset forceReflectionDispatcherUnavailable() throws Exception {
        Field dispatcher = ClassInjector.UsingReflection.class.getDeclaredField("DISPATCHER");
        Unsafe unsafe = unsafe();
        Object fieldBase = unsafe.staticFieldBase(dispatcher);
        long fieldOffset = unsafe.staticFieldOffset(dispatcher);
        Object original = unsafe.getObject(fieldBase, fieldOffset);
        unsafe.putObject(
            fieldBase,
            fieldOffset,
            new Dispatcher.Initializable.Unavailable("Forced unavailable dispatcher"));
        return new Reset(unsafe, fieldBase, fieldOffset, original);
    }

    private static Unsafe unsafe() throws Exception {
        Field unsafe = Unsafe.class.getDeclaredField("theUnsafe");
        unsafe.setAccessible(true);
        return (Unsafe) unsafe.get(null);
    }

    public static final class Reset implements AutoCloseable {
        private final Unsafe unsafe;
        private final Object fieldBase;
        private final long fieldOffset;
        private final Object original;

        private Reset(Unsafe unsafe, Object fieldBase, long fieldOffset, Object original) {
            this.unsafe = unsafe;
            this.fieldBase = fieldBase;
            this.fieldOffset = fieldOffset;
            this.original = original;
        }

        @Override
        public void close() {
            unsafe.putObject(fieldBase, fieldOffset, original);
        }
    }

    public static final class Operations {
        private final Dispatcher dispatcher;

        private Operations(Dispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        public Object getClassLoadingLock(ClassLoader classLoader, String name) {
            return dispatcher.getClassLoadingLock(classLoader, name);
        }

        public Class<?> findClass(ClassLoader classLoader, String name) {
            return dispatcher.findClass(classLoader, name);
        }

        public Class<?> defineClass(
            ClassLoader classLoader,
            String name,
            byte[] binaryRepresentation,
            ProtectionDomain protectionDomain) {
            return dispatcher.defineClass(classLoader, name, binaryRepresentation, protectionDomain);
        }

        public Package getDefinedPackage(ClassLoader classLoader, String name) {
            return dispatcher.getDefinedPackage(classLoader, name);
        }

        public Package getPackage(ClassLoader classLoader, String name) {
            return dispatcher.getPackage(classLoader, name);
        }

        public Package definePackage(
            ClassLoader classLoader,
            String name,
            String specificationTitle,
            String specificationVersion,
            String specificationVendor,
            String implementationTitle,
            String implementationVersion,
            String implementationVendor,
            URL sealBase) {
            return dispatcher.definePackage(
                classLoader,
                name,
                specificationTitle,
                specificationVersion,
                specificationVendor,
                implementationTitle,
                implementationVersion,
                implementationVendor,
                sealBase);
        }
    }

    private static final class PermissionCheckingSystem implements ClassInjector.UsingReflection.System {
        @Override
        public Object getSecurityManager() {
            return new PermissiveSecurityManager();
        }
    }

    private static final class NoSecurityManagerSystem implements ClassInjector.UsingReflection.System {
        @Override
        public Object getSecurityManager() {
            return null;
        }
    }

    @SuppressWarnings("removal")
    private static final class PermissiveSecurityManager extends SecurityManager {
        @Override
        public void checkPermission(Permission permission) {
            // Allow all permissions for the synthetic security manager.
        }
    }

    private abstract static class UnsafeOverrideAccess extends UsingUnsafeOverride {
        UnsafeOverrideAccess() {
            super(null, null, null, null, null);
        }

        static Initializable makeDispatcher() throws Exception {
            return make();
        }
    }

    private abstract static class DirectAccess extends Direct {
        DirectAccess() {
            super(null, null, null, null, null);
        }

        static Initializable makeDispatcher() throws Exception {
            return make();
        }
    }

    private static final class UnavailableAccess extends Unavailable {
        private UnavailableAccess() {
            super("Test unavailable dispatcher");
        }
    }
}
