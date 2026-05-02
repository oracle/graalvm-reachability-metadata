/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.Project;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class AntClassLoaderTest {
    private static final String FIXTURE_CLASS_NAME = "ant.ant.AntClassLoaderFixture";
    private static final String TEST_CLASS_RESOURCE = "ant/ant/AntClassLoaderTest.class";

    @TempDir
    Path temporaryDirectory;

    @Test
    void initializesClassThroughLegacyConstructorHack() {
        assertThatCode(() -> AntClassLoader.initializeClass(
                AntClassLoaderInitializationTarget.class))
                .doesNotThrowAnyException();
    }

    @Test
    void loadsResourcesFromParentBeforeAndAfterLoaderPath() throws IOException {
        AntClassLoader loader = new AntClassLoader(AntClassLoaderTest.class.getClassLoader(), true);

        URL parentFirstUrl = loader.getResource(TEST_CLASS_RESOURCE);
        assertThat(parentFirstUrl).isNotNull();
        try (InputStream parentFirstStream = loader.getResourceAsStream(TEST_CLASS_RESOURCE)) {
            assertThat(parentFirstStream).isNotNull();
        }

        loader.setParentFirst(false);
        URL loaderFirstFallbackUrl = loader.getResource(TEST_CLASS_RESOURCE);
        assertThat(loaderFirstFallbackUrl).isNotNull();

        NullParentAntClassLoader nullParentLoader = new NullParentAntClassLoader(true);
        try (InputStream systemResourceStream = nullParentLoader
                .getResourceAsStream(TEST_CLASS_RESOURCE)) {
            assertThat(systemResourceStream).isNotNull();
        }
    }

    @Test
    void delegatesSystemClassLoadingToParentLoader() throws ClassNotFoundException {
        AntClassLoader loader = new AntClassLoader(
                AntClassLoaderTest.class.getClassLoader(),
                false);
        Class<?> systemClass = loader.forceLoadSystemClass(String.class.getName());
        Class<?> projectClass = loader.loadClass(Project.class.getName());

        NullParentAntClassLoader nullParentLoader = new NullParentAntClassLoader(false);
        Class<?> systemClassFromSystemLoader = nullParentLoader
                .forceLoadSystemClass(String.class.getName());
        Class<?> loadedClassFromSystemLoader = nullParentLoader.loadClass(String.class.getName());

        assertThat(systemClass).isSameAs(String.class);
        assertThat(projectClass).isSameAs(Project.class);
        assertThat(systemClassFromSystemLoader).isSameAs(String.class);
        assertThat(loadedClassFromSystemLoader).isSameAs(String.class);
    }

    @Test
    void forceLoadsClassFromConfiguredPath() throws Exception {
        Path classesDirectory = copyFixtureClassToTemporaryClasspath();
        AntClassLoader loader = new AntClassLoader(
                AntClassLoaderTest.class.getClassLoader(),
                false);
        loader.addPathElement(classesDirectory.toString());

        try {
            Class<?> loadedClass = loader.forceLoadClass(FIXTURE_CLASS_NAME);

            assertThat(loadedClass.getName()).isEqualTo(FIXTURE_CLASS_NAME);
            assertThat(loadedClass.getClassLoader()).isSameAs(loader);
            assertThat(loader.forceLoadClass(FIXTURE_CLASS_NAME)).isSameAs(loadedClass);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private Path copyFixtureClassToTemporaryClasspath() throws IOException {
        String fixtureResourceName = FIXTURE_CLASS_NAME.replace('.', '/') + ".class";
        Path fixtureClassFile = temporaryDirectory.resolve(fixtureResourceName);
        Files.createDirectories(fixtureClassFile.getParent());

        try (InputStream inputStream = AntClassLoaderTest.class.getClassLoader()
                .getResourceAsStream(fixtureResourceName)) {
            assertThat(inputStream).isNotNull();
            Files.copy(inputStream, fixtureClassFile);
        }

        return temporaryDirectory;
    }
}

class AntClassLoaderInitializationTarget {
    // Used as constructor metadata input for AntClassLoader.initializeClass.
}

class AntClassLoaderFixture {
    // Loaded from copied bytecode by AntClassLoader.
}

class NullParentAntClassLoader extends AntClassLoader {
    NullParentAntClassLoader(boolean parentFirst) {
        super(AntClassLoaderTest.class.getClassLoader(), parentFirst);
    }

    @Override
    public void setParent(ClassLoader parent) {
        // Preserve AntClassLoader's legacy no-parent branch for public API coverage.
    }
}
