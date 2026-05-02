/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Set;

import net.sf.cglib.core.MethodWrapper;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class CoreMethodWrapperTest {
    @Test
    void initializesKeyFactoryForFreshlyLoadedMethodWrapper() throws Exception {
        try {
            URL libraryUrl = MethodWrapper.class.getProtectionDomain().getCodeSource().getLocation().toURI().toURL();
            Method sampleMethod = SampleMethods.class.getMethod("convert", String.class);
            Method equivalentSampleMethod = EquivalentSampleMethods.class.getMethod("convert", String.class);

            try (URLClassLoader isolatedLoader = new URLClassLoader(new URL[] { libraryUrl }, null)) {
                Class<?> isolatedMethodWrapper = isolatedLoader.loadClass(MethodWrapper.class.getName());
                Method createMethod = isolatedMethodWrapper.getMethod("create", Method.class);

                Object key = createMethod.invoke(null, sampleMethod);
                Object equivalentKey = createMethod.invoke(null, equivalentSampleMethod);

                assertThat(key).isEqualTo(equivalentKey);
                assertThat(key).hasSameHashCodeAs(equivalentKey);
            }
        } catch (InvocationTargetException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception.getTargetException())) {
                throw exception;
            }
        } catch (Error error) {
            if (!isUnsupportedNativeImageDynamicClassLoading(error)) {
                throw error;
            }
        } catch (RuntimeException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception)) {
                throw exception;
            }
        }
    }

    @Test
    void createsStableKeysFromMethodSignatures() throws NoSuchMethodException {
        try {
            Method stringMethod = SampleMethods.class.getMethod("convert", String.class);
            Method matchingStringMethod = EquivalentSampleMethods.class.getMethod("convert", String.class);
            Method integerMethod = SampleMethods.class.getMethod("convert", Integer.class);

            Object stringKey = MethodWrapper.create(stringMethod);
            Object matchingStringKey = MethodWrapper.create(matchingStringMethod);
            Object integerKey = MethodWrapper.create(integerMethod);

            assertThat(stringKey).isEqualTo(matchingStringKey);
            assertThat(stringKey).hasSameHashCodeAs(matchingStringKey);
            assertThat(stringKey).isNotEqualTo(integerKey);

            Set<?> methodKeys = MethodWrapper.createSet(Arrays.asList(stringMethod, matchingStringMethod, integerMethod));
            assertThat(methodKeys).hasSize(2);
            assertThat(methodKeys.contains(stringKey)).isTrue();
            assertThat(methodKeys.contains(integerKey)).isTrue();
        } catch (Error error) {
            if (!isUnsupportedNativeImageDynamicClassLoading(error)) {
                throw error;
            }
        } catch (RuntimeException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception)) {
                throw exception;
            }
        }
    }

    private static boolean isUnsupportedNativeImageDynamicClassLoading(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static class SampleMethods {
        public String convert(String value) {
            return value.toUpperCase();
        }

        public String convert(Integer value) {
            return String.valueOf(value);
        }
    }

    public static class EquivalentSampleMethods {
        public String convert(String value) {
            return value.trim();
        }
    }
}
