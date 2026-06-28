/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_conscrypt.conscrypt_openjdk_uber;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.conscrypt.Conscrypt;
import org.junit.jupiter.api.Test;

public class NativeLibraryLoaderAnonymous2Test {
    private static final String NATIVE_LIBRARY_LOADER_CLASS_NAME =
            "org.conscrypt.NativeLibraryLoader";
    private static final String NATIVE_LIBRARY_UTIL_CLASS_NAME =
            "org.conscrypt.NativeLibraryUtil";

    @Test
    void helperClassCanBeDefinedIntoLoaderThatCannotLoadIt() throws Exception {
        Conscrypt.checkAvailability();

        Class<?> nativeLibraryLoaderClass = Class.forName(NATIVE_LIBRARY_LOADER_CLASS_NAME);
        Class<?> nativeLibraryUtilClass = Class.forName(NATIVE_LIBRARY_UTIL_CLASS_NAME);
        Method tryToLoadClass = nativeLibraryLoaderClass.getDeclaredMethod(
                "tryToLoadClass", ClassLoader.class, Class.class);
        tryToLoadClass.setAccessible(true);

        ClassLoader targetLoader = new HelperHidingClassLoader(nativeLibraryUtilClass.getName());
        Class<?> definedHelperClass = (Class<?>) tryToLoadClass.invoke(
                null, targetLoader, nativeLibraryUtilClass);

        assertThat(definedHelperClass.getName()).isEqualTo(nativeLibraryUtilClass.getName());
        assertThat(definedHelperClass.getClassLoader()).isSameAs(targetLoader);
        assertThat(definedHelperClass).isNotSameAs(nativeLibraryUtilClass);
    }

    private static final class HelperHidingClassLoader extends ClassLoader {
        private final String hiddenClassName;

        private HelperHidingClassLoader(String hiddenClassName) {
            super(null);
            this.hiddenClassName = hiddenClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (hiddenClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }
}
