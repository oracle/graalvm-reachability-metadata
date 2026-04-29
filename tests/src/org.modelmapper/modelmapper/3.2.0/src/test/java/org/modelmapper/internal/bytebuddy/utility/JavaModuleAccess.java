/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.modelmapper.internal.bytebuddy.utility;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Set;

import sun.misc.Unsafe;

public final class JavaModuleAccess {
    private JavaModuleAccess() {
    }

    public static Reset forceUnreadableModules() throws Exception {
        Field module = JavaModule.class.getDeclaredField("MODULE");
        Unsafe unsafe = unsafe();
        Object fieldBase = unsafe.staticFieldBase(module);
        long fieldOffset = unsafe.staticFieldOffset(module);
        JavaModule.Module original = (JavaModule.Module) unsafe.getObject(fieldBase, fieldOffset);
        unsafe.putObject(fieldBase, fieldOffset, new UnreadableModule(original));
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
        private final JavaModule.Module original;

        private Reset(Unsafe unsafe, Object fieldBase, long fieldOffset, JavaModule.Module original) {
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

    private static final class UnreadableModule implements JavaModule.Module {
        private final JavaModule.Module delegate;

        private UnreadableModule(JavaModule.Module delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isInstance(Object value) {
            return delegate.isInstance(value);
        }

        @Override
        public boolean isNamed(Object module) {
            return delegate.isNamed(module);
        }

        @Override
        public String getName(Object module) {
            return delegate.getName(module);
        }

        @Override
        public Set<String> getPackages(Object module) {
            return delegate.getPackages(module);
        }

        @Override
        public ClassLoader getClassLoader(Object module) {
            return delegate.getClassLoader(module);
        }

        @Override
        public InputStream getResourceAsStream(Object module, String name) throws IOException {
            return delegate.getResourceAsStream(module, name);
        }

        @Override
        public boolean isExported(Object source, String packageName, Object target) {
            return delegate.isExported(source, packageName, target);
        }

        @Override
        public boolean isOpen(Object source, String packageName, Object target) {
            return delegate.isOpen(source, packageName, target);
        }

        @Override
        public boolean canRead(Object source, Object target) {
            return false;
        }
    }
}
