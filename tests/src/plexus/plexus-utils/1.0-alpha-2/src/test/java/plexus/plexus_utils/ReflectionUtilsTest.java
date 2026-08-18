/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package plexus.plexus_utils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;

import org.codehaus.plexus.util.ReflectionUtils;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilsTest {
    @Test
    void findsFieldsDeclaredOnSuperclasses() {
        Field field = ReflectionUtils.getFieldByNameIncludingSuperclasses("inheritedValue", ChildBean.class);

        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("inheritedValue");
        assertThat(field.getDeclaringClass()).isEqualTo(ParentBean.class);
    }

    @Test
    void returnsNullWhenFieldIsMissingFromEntireHierarchy() {
        Field field = ReflectionUtils.getFieldByNameIncludingSuperclasses("missingValue", ChildBean.class);

        assertThat(field).isNull();
    }

    @Test
    void findsSuperclassFieldsWhenLoadedInAnIsolatedClassLoader() throws Throwable {
        try {
            Class<?> isolatedReflectionUtils = new IsolatedReflectionUtilsLoader()
                    .loadClass(ReflectionUtils.class.getName());
            MethodHandle getFieldByNameIncludingSuperclasses = MethodHandles.publicLookup()
                    .findStatic(
                            isolatedReflectionUtils,
                            "getFieldByNameIncludingSuperclasses",
                            MethodType.methodType(Field.class, String.class, Class.class));

            Field field = (Field) getFieldByNameIncludingSuperclasses.invoke("inheritedValue", ChildBean.class);

            assertThat(field).isNotNull();
            assertThat(field.getDeclaringClass()).isEqualTo(ParentBean.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static class ParentBean {
        public String inheritedValue;
    }

    public static class ChildBean extends ParentBean {
        public void setName(String name) {
            inheritedValue = name;
        }
    }

    private static final class IsolatedReflectionUtilsLoader extends ClassLoader {
        IsolatedReflectionUtilsLoader() {
            super(ReflectionUtilsTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!ReflectionUtils.class.getName().equals(name)) {
                return super.loadClass(name, resolve);
            }

            Class<?> loadedClass = findLoadedClass(name);
            if (loadedClass == null) {
                loadedClass = findClass(name);
            }
            if (resolve) {
                resolveClass(loadedClass);
            }
            return loadedClass;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!ReflectionUtils.class.getName().equals(name)) {
                return super.findClass(name);
            }

            String resourceName = name.replace('.', '/') + ".class";
            try (InputStream inputStream = getParent().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] classBytes = inputStream.readAllBytes();
                return defineClass(name, classBytes, 0, classBytes.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }
    }
}
