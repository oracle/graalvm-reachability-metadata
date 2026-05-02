/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.InputStream;

import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.helper.ProjectHelper2;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.assertj.core.api.Assertions.assertThat;

@ResourceLock("ant-project-helper-discovery")
public class ProjectHelperTest {
    private static final String DEFAULT_HELPER_CLASS_NAME = ProjectHelper2.class.getName();

    private ClassLoader originalContextClassLoader;
    private String originalProjectHelperProperty;

    @BeforeEach
    void rememberGlobalDiscoveryState() {
        originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        originalProjectHelperProperty = System.getProperty(ProjectHelper.HELPER_PROPERTY);
    }

    @AfterEach
    void restoreGlobalDiscoveryState() {
        Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        setProjectHelperProperty(originalProjectHelperProperty);
    }

    @Test
    void createsConfiguredHelperThroughContextClassLoader() {
        Thread.currentThread().setContextClassLoader(ProjectHelperTest.class.getClassLoader());
        setProjectHelperProperty(DEFAULT_HELPER_CLASS_NAME);

        ProjectHelper helper = ProjectHelper.getProjectHelper();

        assertThat(helper).isInstanceOf(ProjectHelper2.class);
    }

    @Test
    void fallsBackToClassForNameWhenContextClassLoaderMissesConfiguredHelper() {
        ClassLoader contextClassLoader = new MissingProjectHelperClassLoader(
                ProjectHelperTest.class.getClassLoader(),
                DEFAULT_HELPER_CLASS_NAME);
        Thread.currentThread().setContextClassLoader(contextClassLoader);
        setProjectHelperProperty(DEFAULT_HELPER_CLASS_NAME);

        ProjectHelper helper = ProjectHelper.getProjectHelper();

        assertThat(helper).isInstanceOf(ProjectHelper2.class);
    }

    @Test
    void searchesContextAndSystemServiceResourcesBeforeUsingDefaultHelper() {
        Thread.currentThread().setContextClassLoader(new EmptyServiceClassLoader(
                ProjectHelperTest.class.getClassLoader()));
        System.clearProperty(ProjectHelper.HELPER_PROPERTY);

        ProjectHelper helper = ProjectHelper.getProjectHelper();

        assertThat(helper).isNotNull();
    }

    private static void setProjectHelperProperty(String value) {
        if (value == null) {
            System.clearProperty(ProjectHelper.HELPER_PROPERTY);
        } else {
            System.setProperty(ProjectHelper.HELPER_PROPERTY, value);
        }
    }

    private static final class MissingProjectHelperClassLoader extends ClassLoader {
        private final String missingClassName;

        MissingProjectHelperClassLoader(ClassLoader parent, String missingClassName) {
            super(parent);
            this.missingClassName = missingClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (missingClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }

    private static final class EmptyServiceClassLoader extends ClassLoader {
        EmptyServiceClassLoader(ClassLoader parent) {
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
}
