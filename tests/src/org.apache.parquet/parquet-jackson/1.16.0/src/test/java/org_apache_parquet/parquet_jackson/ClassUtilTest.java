/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Method;
import java.util.Base64;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.databind.util.ClassUtil;

public class ClassUtilTest {
    private static final String SUBJECT_CLASS_NAME =
            "org_apache_parquet.parquet_jackson.ClassUtilTestSubject";
    private static final String DEPENDENCY_CLASS_NAME =
            "org_apache_parquet.parquet_jackson.ClassUtilTestDependency";
    private static final String SUBJECT_CLASS_BYTES = """
            yv66vgAAADQAEgoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClW\
            BwAIAQA6b3JnX2FwYWNoZV9wYXJxdWV0L3BhcnF1ZXRfamFja3Nvbi9DbGFzc1V0aWxUZXN0RGVw\
            ZW5kZW5jeQoABwADBwALAQA3b3JnX2FwYWNoZV9wYXJxdWV0L3BhcnF1ZXRfamFja3Nvbi9DbGFz\
            c1V0aWxUZXN0U3ViamVjdAEABENvZGUBAA9MaW5lTnVtYmVyVGFibGUBAApkZXBlbmRlbmN5\
            AQA+KClMb3JnX2FwYWNoZV9wYXJxdWV0L3BhcnF1ZXRfamFja3Nvbi9DbGFzc1V0aWxUZXN0RGVw\
            ZW5kZW5jeTsBAApTb3VyY2VGaWxlAQAZQ2xhc3NVdGlsVGVzdFN1YmplY3QuamF2YQAxAAoAAg\
            AAAAAAAgABAAUABgABAAwAAAAdAAEAAQAAAAUqtwABsQAAAAEADQAAAAYAAQAAAAMAAQAOAA8A\
            AQAMAAAAIAACAAEAAAAIuwAHWbcACbAAAAABAA0AAAAGAAEAAAAFAAEAEAAAAAIAEQ==\
            """;

    @Test
    void createsInstancesAndFindsDefaultConstructor() {
        final ClassUtilTestBean bean = ClassUtil.createInstance(ClassUtilTestBean.class, true);

        assertThat(bean.value()).isEqualTo("created");
        assertThat(ClassUtil.findConstructor(ClassUtilTestBean.class, true).getDeclaringClass())
                .isEqualTo(ClassUtilTestBean.class);
    }

    @Test
    void exposesDeclaredMembersAndConstructors() {
        assertThat(ClassUtil.getDeclaredFields(ClassUtilTestBean.class))
                .extracting(field -> field.getName())
                .contains("value");
        assertThat(ClassUtil.getDeclaredMethods(ClassUtilTestBean.class))
                .extracting(Method::getName)
                .contains("value");
        assertThat(ClassUtil.getClassMethods(ClassUtilTestBean.class))
                .extracting(Method::getName)
                .contains("value");
        assertThat(ClassUtil.getConstructors(ClassUtilTestBean.class))
                .anySatisfy(ctor -> assertThat(ctor.getParamCount()).isZero());
    }

    @Test
    void findsAnnotatedEnumValue() {
        final Enum<?> enumValue = findFirstMarkerValue();

        assertThat(enumValue).isEqualTo(ClassUtilTestAnnotatedEnum.SELECTED);
    }

    @Test
    void reloadsClassMethodsWithThreadContextClassLoaderWhenInitialResolutionFails() throws Exception {
        final ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ClassUtilTest.class.getClassLoader());
        try {
            final Class<?> isolatedSubjectClass = new DependencyHidingClassLoader().loadClass(SUBJECT_CLASS_NAME);

            assertThat(ClassUtil.getClassMethods(isolatedSubjectClass))
                    .extracting(Method::getName)
                    .contains("dependency");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Enum<?> findFirstMarkerValue() {
        return ClassUtil.findFirstAnnotatedEnumValue(
                (Class) ClassUtilTestAnnotatedEnum.class, ClassUtilTestMarker.class);
    }

    private static final class DependencyHidingClassLoader extends ClassLoader {
        DependencyHidingClassLoader() {
            super(null);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (SUBJECT_CLASS_NAME.equals(name)) {
                final Class<?> subjectClass = findClass(name);
                if (resolve) {
                    resolveClass(subjectClass);
                }
                return subjectClass;
            }
            if (DEPENDENCY_CLASS_NAME.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!SUBJECT_CLASS_NAME.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            final byte[] bytes = Base64.getDecoder().decode(SUBJECT_CLASS_BYTES);
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}

final class ClassUtilTestBean {
    private final String value;

    ClassUtilTestBean() {
        this.value = "created";
    }

    String value() {
        return value;
    }
}

@Retention(RetentionPolicy.RUNTIME)
@interface ClassUtilTestMarker {
}

enum ClassUtilTestAnnotatedEnum {
    FIRST,

    @ClassUtilTestMarker
    SELECTED
}

final class ClassUtilTestDependency {
}

final class ClassUtilTestSubject {
    ClassUtilTestDependency dependency() {
        return new ClassUtilTestDependency();
    }
}
