/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.apache.tools.ant.loader.AntClassLoader2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class AntClassLoader2Test {
    private static final String FIXTURE_CLASS_NAME = "fixtures.AntLoadedFixture";
    private static final String FIXTURE_CLASS_RESOURCE = "fixtures/AntLoadedFixture.class";
    private static final String FIXTURE_CLASS_BYTES = "yv66vgAAADQAEQoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcv"
            + "T2JqZWN0AQAGPGluaXQ+AQADKClWCAAIAQAGbG9hZGVkBwAKAQAZZml4dHVyZXMv"
            + "QW50TG9hZGVkRml4dHVyZQEABENvZG"
            + "UBAA9MaW5lTnVtYmVyVGFibGUBAAdtZXNzYWdlAQAUKClMamF2YS9sYW5nL1N0cmluZzsBAApT"
            + "b3VyY2VGaWxlAQAVQW50TG9hZGVkRml4dHVyZS5qYXZhACEACQACAAAAAAACAAEABQAGAAEACw"
            + "AAAB0AAQABAAAABSq3AAGxAAAAAQAMAAAABgABAAAAAgABAA0ADgABAAsAAAAbAAEAAQAAAAMS"
            + "B7AAAAABAAwAAAAGAAEAAAAEAAEADwAAAAIAEA==";

    @Test
    void exposedDefineClassUsesProjectProtectionDomain(@TempDir Path temporaryDirectory) throws Exception {
        ExposedAntClassLoader2 loader = new ExposedAntClassLoader2();
        try {
            Class<?> loadedClass;
            try {
                loadedClass = loader.defineFixtureClass(temporaryDirectory.toFile(),
                        Base64.getDecoder().decode(FIXTURE_CLASS_BYTES));
            } catch (Error ex) {
                assertRuntimeClassDefinitionUnsupported(ex);
                return;
            }

            assertLoadedFixture(loadedClass, loader);
        } finally {
            loader.cleanup();
        }
    }

    @Test
    void forceLoadClassDefinesFixtureWithProjectProtectionDomain(@TempDir Path temporaryDirectory) throws Exception {
        Path classesDirectory = temporaryDirectory.resolve("classes");
        Path fixtureClassFile = classesDirectory.resolve(FIXTURE_CLASS_RESOURCE);
        Files.createDirectories(fixtureClassFile.getParent());
        Files.write(fixtureClassFile, Base64.getDecoder().decode(FIXTURE_CLASS_BYTES));

        AntClassLoader2 loader = new AntClassLoader2();
        loader.addPathElement(classesDirectory.toString());
        try {
            assertThat(loader.getResource(FIXTURE_CLASS_RESOURCE)).isNotNull();

            Class<?> loadedClass;
            try {
                loadedClass = loader.forceLoadClass(FIXTURE_CLASS_NAME);
            } catch (Error ex) {
                assertRuntimeClassDefinitionUnsupported(ex);
                return;
            }

            assertLoadedFixture(loadedClass, loader);
        } finally {
            loader.cleanup();
        }
    }

    private static void assertLoadedFixture(Class<?> loadedClass, AntClassLoader2 loader) {
        assertThat(loadedClass.getName()).isEqualTo(FIXTURE_CLASS_NAME);
        assertThat(loadedClass.getClassLoader()).isSameAs(loader);
        assertThat(loadedClass.getPackage().getName()).isEqualTo("fixtures");
    }

    private static void assertRuntimeClassDefinitionUnsupported(Error ex) {
        assertThat(ex)
                .hasMessageContaining("Classes cannot be defined at runtime")
                .hasMessageContaining(FIXTURE_CLASS_NAME);
    }

    private static final class ExposedAntClassLoader2 extends AntClassLoader2 {
        private Class<?> defineFixtureClass(File container, byte[] classData) throws IOException {
            return defineClassFromData(container, classData, FIXTURE_CLASS_NAME);
        }
    }
}
