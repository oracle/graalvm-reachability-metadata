/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.helper.ProjectHelper2;
import org.junit.jupiter.api.Test;

public class ProjectHelperTest {
    private static final String PROJECT_HELPER2_NAME = ProjectHelper2.class.getName();

    @Test
    void loadsHelperNamedBySystemProperty() {
        ProjectHelper helper = withProjectHelperProperty(PROJECT_HELPER2_NAME, () -> ProjectHelper.getProjectHelper());

        assertThat(helper).isInstanceOf(ProjectHelper2.class);
    }

    @Test
    void fallsBackToClassForNameWhenContextLoaderCannotLoadHelper() {
        ClassLoader originalLoader = currentThread().getContextClassLoader();
        currentThread().setContextClassLoader(new RejectingProjectHelperClassLoader(originalLoader));
        try {
            ProjectHelper helper = withProjectHelperProperty(PROJECT_HELPER2_NAME, () -> ProjectHelper.getProjectHelper());

            assertThat(helper).isInstanceOf(ProjectHelper2.class);
        } finally {
            currentThread().setContextClassLoader(originalLoader);
        }
    }

    @Test
    void discoversHelperFromContextLoaderServiceResource() {
        ClassLoader originalLoader = currentThread().getContextClassLoader();
        currentThread().setContextClassLoader(new ProjectHelperServiceClassLoader(originalLoader));
        try {
            ProjectHelper helper = withoutProjectHelperProperty(() -> ProjectHelper.getProjectHelper());

            assertThat(helper).isInstanceOf(ProjectHelper2.class);
        } finally {
            currentThread().setContextClassLoader(originalLoader);
        }
    }

    @Test
    void checksSystemServiceResourceWhenContextLoaderHasNoServiceResource() {
        ClassLoader originalLoader = currentThread().getContextClassLoader();
        currentThread().setContextClassLoader(new EmptyServiceClassLoader(originalLoader));
        try {
            ProjectHelper helper = withoutProjectHelperProperty(() -> ProjectHelper.getProjectHelper());

            assertThat(helper).isNotNull();
        } finally {
            currentThread().setContextClassLoader(originalLoader);
        }
    }

    private static ProjectHelper withProjectHelperProperty(String helperClassName, ProjectHelperAction action) {
        String previousValue = System.getProperty(ProjectHelper.HELPER_PROPERTY);
        System.setProperty(ProjectHelper.HELPER_PROPERTY, helperClassName);
        try {
            return action.get();
        } finally {
            restoreProjectHelperProperty(previousValue);
        }
    }

    private static ProjectHelper withoutProjectHelperProperty(ProjectHelperAction action) {
        String previousValue = System.getProperty(ProjectHelper.HELPER_PROPERTY);
        System.clearProperty(ProjectHelper.HELPER_PROPERTY);
        try {
            return action.get();
        } finally {
            restoreProjectHelperProperty(previousValue);
        }
    }

    private static void restoreProjectHelperProperty(String previousValue) {
        if (previousValue == null) {
            System.clearProperty(ProjectHelper.HELPER_PROPERTY);
        } else {
            System.setProperty(ProjectHelper.HELPER_PROPERTY, previousValue);
        }
    }

    private static Thread currentThread() {
        return Thread.currentThread();
    }

    private interface ProjectHelperAction {
        ProjectHelper get();
    }

    private static final class ProjectHelperServiceClassLoader extends ClassLoader {
        private final byte[] serviceContent;

        private ProjectHelperServiceClassLoader(ClassLoader parent) {
            super(parent);
            this.serviceContent = (PROJECT_HELPER2_NAME + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (ProjectHelper.SERVICE_ID.equals(name)) {
                return new ByteArrayInputStream(serviceContent);
            }
            return super.getResourceAsStream(name);
        }
    }

    private static final class EmptyServiceClassLoader extends ClassLoader {
        private EmptyServiceClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (ProjectHelper.SERVICE_ID.equals(name)) {
                return null;
            }
            return super.getResourceAsStream(name);
        }
    }

    private static final class RejectingProjectHelperClassLoader extends ClassLoader {
        private RejectingProjectHelperClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (PROJECT_HELPER2_NAME.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }
}
