/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.scopedpool.ScopedClassPool;
import javassist.scopedpool.ScopedClassPoolRepository;
import javassist.scopedpool.ScopedClassPoolRepositoryImpl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class ScopedClassPoolTest {
    private static final String FIXTURE_CLASS_NAME = "example.ScopedPoolFixture";

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesClassResourceThroughScopedClassLoader() throws Exception {
        writeGeneratedClassFile();
        ClassLoader loader = new ClassFileResourceLoader(temporaryDirectory);
        ClassPool sourcePool = new ClassPool(true);
        ScopedClassPoolRepository repository = ScopedClassPoolRepositoryImpl.getInstance();
        ScopedClassPool scopedPool = repository.createScopedClassPool(loader, sourcePool);
        try {
            CtClass resolvedClass = scopedPool.get(FIXTURE_CLASS_NAME);

            assertThat(resolvedClass.getName()).isEqualTo(FIXTURE_CLASS_NAME);
        } finally {
            scopedPool.close();
        }
    }

    private void writeGeneratedClassFile() throws Exception {
        ClassPool generationPool = new ClassPool(true);
        CtClass generatedClass = generationPool.makeClass(FIXTURE_CLASS_NAME);
        try {
            Path classFile = temporaryDirectory.resolve("example/ScopedPoolFixture.class");
            Files.createDirectories(classFile.getParent());
            Files.write(classFile, generatedClass.toBytecode());
        } finally {
            generatedClass.detach();
        }
    }

    private static final class ClassFileResourceLoader extends ClassLoader {
        private final Path rootDirectory;

        private ClassFileResourceLoader(Path rootDirectory) {
            super(null);
            this.rootDirectory = rootDirectory;
        }

        @Override
        public URL getResource(String name) {
            Path resource = rootDirectory.resolve(name);
            if (!Files.isRegularFile(resource)) {
                return null;
            }

            try {
                return resource.toUri().toURL();
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            Path resource = rootDirectory.resolve(name);
            if (!Files.isRegularFile(resource)) {
                return null;
            }

            try {
                return Files.newInputStream(resource);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }
}
