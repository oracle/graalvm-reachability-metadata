/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javassist.tools.web.Viewer;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ViewerTest {
    private static final String RUN_RESULT_PROPERTY = "javassist.viewer.test.result";

    @Test
    void runFetchesApplicationClassAndInvokesMainMethod() throws Throwable {
        System.clearProperty(RUN_RESULT_PROPERTY);
        ClasspathBackedViewer viewer = new ClasspathBackedViewer();

        try {
            viewer.run(ViewerRunnableApplication.class.getName(), new String[] { "left", "right" });

            assertThat(System.getProperty(RUN_RESULT_PROPERTY)).isEqualTo("left:right");
        } catch (Error error) {
            verifyUnsupportedDynamicClassLoading(error);
        } finally {
            System.clearProperty(RUN_RESULT_PROPERTY);
        }
    }

    @Test
    void loadClassFindsCachedClassAndDelegatesCoreClassToSystemLoader() throws ClassNotFoundException {
        ClasspathBackedViewer viewer = new ClasspathBackedViewer();

        try {
            Class<?> loadedClass = viewer.loadClass(ViewerCachedApplication.class.getName());
            Class<?> cachedClass = viewer.loadClass(ViewerCachedApplication.class.getName());

            assertThat(cachedClass).isSameAs(loadedClass);
            assertThat(loadedClass.getClassLoader()).isSameAs(viewer);
        } catch (Error error) {
            verifyUnsupportedDynamicClassLoading(error);
        }

        assertThat(viewer.loadClass(String.class.getName())).isSameAs(String.class);
    }

    private static void verifyUnsupportedDynamicClassLoading(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    public static class ViewerRunnableApplication {
        public static void main(String[] args) {
            System.setProperty("javassist.viewer.test.result", args[0] + ":" + args[1]);
        }
    }

    public static class ViewerCachedApplication {
    }

    private static class ClasspathBackedViewer extends Viewer {
        ClasspathBackedViewer() {
            super("localhost", -1);
        }

        @Override
        protected byte[] fetchClass(String classname) throws IOException {
            String resourceName = classname.replace('.', '/') + ".class";
            InputStream stream = ViewerTest.class.getClassLoader().getResourceAsStream(resourceName);
            if (stream == null) {
                return null;
            }

            try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        }
    }
}
