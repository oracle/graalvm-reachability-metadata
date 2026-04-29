/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.modelmapper.internal.bytebuddy.dynamic.loading;

import java.security.ProtectionDomain;
import java.util.Map;

import org.modelmapper.internal.bytebuddy.dynamic.loading.ClassInjector.UsingUnsafe.Dispatcher;

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

    private static final class TestableUsingUnsafe extends ClassInjector.UsingUnsafe {
        TestableUsingUnsafe(ClassLoader classLoader, Dispatcher.Initializable dispatcher) {
            super(classLoader, protectionDomain(), dispatcher);
        }

        private static ProtectionDomain protectionDomain() {
            return UsingUnsafeAccess.class.getProtectionDomain();
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
