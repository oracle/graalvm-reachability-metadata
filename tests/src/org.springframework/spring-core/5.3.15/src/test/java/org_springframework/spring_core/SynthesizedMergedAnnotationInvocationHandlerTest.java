/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.LinkedHashMap;
import java.util.Map;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.SynthesizedAnnotation;

public class SynthesizedMergedAnnotationInvocationHandlerTest {

    private static final String CHILD_LOADED_ANNOTATION_NAME =
            "org_springframework.spring_core.SynthesizedMergedAnnotationInvocationHandlerTest$ChildLoadedAnnotation";

    @Test
    void synthesizesAnnotationLoadedByChildClassLoader() throws Exception {
        try {
            ChildFirstAnnotationClassLoader classLoader = new ChildFirstAnnotationClassLoader(
                    SynthesizedMergedAnnotationInvocationHandlerTest.class.getClassLoader()
            );
            Class<? extends Annotation> annotationType = classLoader.loadClass(CHILD_LOADED_ANNOTATION_NAME)
                    .asSubclass(Annotation.class);
            Map<String, Object> attributes = new LinkedHashMap<>();
            attributes.put("value", "from-child-loader");

            Annotation synthesized = MergedAnnotation.of(annotationType, attributes).synthesize();

            assertThat(annotationType.isInstance(synthesized)).isTrue();
            assertThat(synthesized).isInstanceOf(SynthesizedAnnotation.class);
            assertThat(synthesized.annotationType()).isSameAs(annotationType);
            assertThat(synthesized.toString()).contains("from-child-loader");
        }
        catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface ChildLoadedAnnotation {

        String value();
    }

    private static final class ChildFirstAnnotationClassLoader extends ClassLoader {

        ChildFirstAnnotationClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    if (CHILD_LOADED_ANNOTATION_NAME.equals(name)) {
                        loadedClass = findClass(name);
                    }
                    else {
                        loadedClass = super.loadClass(name, false);
                    }
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (!CHILD_LOADED_ANNOTATION_NAME.equals(name)) {
                return super.findClass(name);
            }
            String resourceName = name.replace('.', '/') + ".class";
            try (InputStream inputStream = getParent().getResourceAsStream(resourceName)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] bytes = inputStream.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            }
            catch (IOException ex) {
                throw new ClassNotFoundException(name, ex);
            }
        }
    }
}
