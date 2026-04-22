/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NativeLibraryLoader2Test {

    private static final String NATIVE_LIBRARY_LOADER_CLASS_NAME = "org.conscrypt.NativeLibraryLoader";
    private static final String NATIVE_LIBRARY_UTIL_CLASS_NAME = "org.conscrypt.NativeLibraryUtil";

    @Test
    void definesHelperClassIntoTargetClassLoaderWhenHelperCannotBeLoaded() throws Exception {
        Class<?> helperClass = Class.forName(NATIVE_LIBRARY_UTIL_CLASS_NAME);
        RejectingHelperClassLoader targetClassLoader = new RejectingHelperClassLoader();

        Class<?> definedHelperClass = invokeTryToLoadClass(targetClassLoader, helperClass);

        assertThat(definedHelperClass.getName()).isEqualTo(NATIVE_LIBRARY_UTIL_CLASS_NAME);
        assertThat(definedHelperClass).isNotSameAs(helperClass);
        assertThat(definedHelperClass.getClassLoader()).isSameAs(targetClassLoader);
        assertThat(targetClassLoader.helperClassLoadAttempts).isGreaterThan(0);
    }

    private static Class<?> invokeTryToLoadClass(ClassLoader targetClassLoader, Class<?> helperClass) throws Exception {
        Class<?> nativeLibraryLoaderClass = Class.forName(NATIVE_LIBRARY_LOADER_CLASS_NAME);
        Method tryToLoadClass = nativeLibraryLoaderClass.getDeclaredMethod("tryToLoadClass", ClassLoader.class, Class.class);
        tryToLoadClass.setAccessible(true);
        try {
            return (Class<?>) tryToLoadClass.invoke(null, targetClassLoader, helperClass);
        } catch (InvocationTargetException invocationTargetException) {
            Throwable targetException = invocationTargetException.getTargetException();
            if (targetException instanceof Exception exception) {
                throw exception;
            }
            if (targetException instanceof Error error) {
                throw error;
            }
            throw invocationTargetException;
        }
    }

    private static final class RejectingHelperClassLoader extends ClassLoader {

        private int helperClassLoadAttempts;

        private RejectingHelperClassLoader() {
            super(ClassLoader.getPlatformClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (NATIVE_LIBRARY_UTIL_CLASS_NAME.equals(name)) {
                helperClassLoadAttempts++;
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
