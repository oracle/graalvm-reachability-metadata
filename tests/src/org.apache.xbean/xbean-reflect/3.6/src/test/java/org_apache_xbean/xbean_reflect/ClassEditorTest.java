/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_xbean.xbean_reflect;

import org.apache.xbean.propertyeditor.ClassEditor;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassEditorTest {
    @Test
    void convertsMetadataRegisteredClassNameWithACustomContextClassLoader() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader customContextClassLoader = new ClassLoader(originalClassLoader) {
        };

        try {
            Thread.currentThread().setContextClassLoader(customContextClassLoader);

            ClassEditor editor = new ClassEditor();
            editor.setAsText(ContextLoadedTarget.class.getName());

            assertThat(editor.getValue()).isSameAs(ContextLoadedTarget.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    public static final class ContextLoadedTarget {
    }
}
