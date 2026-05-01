/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_reflect;

import java.util.ArrayList;
import java.util.List;

import org.apache.xbean.propertyeditor.ClassEditor;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassEditorTest {
    @Test
    void convertsClassNameUsingThreadContextClassLoader() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        RecordingClassLoader recordingClassLoader = new RecordingClassLoader(originalClassLoader);

        try {
            Thread.currentThread().setContextClassLoader(recordingClassLoader);

            ClassEditor editor = new ClassEditor();
            editor.setAsText(ArrayList.class.getName());

            assertThat(editor.getValue()).isSameAs(ArrayList.class);
            assertThat(recordingClassLoader.getLoadedClassNames()).contains(ArrayList.class.getName());
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private static class RecordingClassLoader extends ClassLoader {
        private final List<String> loadedClassNames = new ArrayList<>();

        RecordingClassLoader(ClassLoader parent) {
            super(parent);
        }

        List<String> getLoadedClassNames() {
            return loadedClassNames;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            loadedClassNames.add(name);
            return super.loadClass(name, resolve);
        }
    }
}
