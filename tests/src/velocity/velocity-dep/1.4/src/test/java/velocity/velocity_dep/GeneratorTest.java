/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.log.NullLogSystem;
import org.apache.velocity.texen.Generator;
import org.apache.velocity.texen.util.FileUtil;
import org.apache.velocity.texen.util.PropertiesUtil;
import org.apache.velocity.util.StringUtils;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class GeneratorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void initializesGeneratorInIsolatedLoader() throws Exception {
        try {
            final IsolatedGeneratorClassLoader classLoader = new IsolatedGeneratorClassLoader(
                    Generator.class.getClassLoader());
            final Class<?> generatorClass = Class.forName(
                    IsolatedGeneratorClassLoader.GENERATOR_CLASS_NAME,
                    true,
                    classLoader);

            assertExpectedGeneratorLoader(generatorClass.getClassLoader(), classLoader);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void parsesControlTemplateWithDefaultTexenContextObjects() throws Exception {
        final Path templateDirectory = temporaryDirectory.resolve("templates");
        final Path outputDirectory = temporaryDirectory.resolve("generated");
        Files.createDirectories(templateDirectory);
        Files.createDirectories(outputDirectory);
        Files.writeString(
                templateDirectory.resolve("control.vm"),
                "Generated $name in $outputDirectory",
                StandardCharsets.UTF_8);

        final VelocityEngine engine = new VelocityEngine();
        engine.setProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM, new NullLogSystem());
        engine.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_PATH, templateDirectory.toString());
        engine.setProperty(RuntimeConstants.VM_LIBRARY, "");
        engine.init();

        final Generator generator = new Generator(temporaryDirectory.resolve("missing-texen.properties").toString());
        generator.setVelocityEngine(engine);
        generator.setTemplatePath(templateDirectory.toString());
        generator.setOutputPath(outputDirectory.toString());

        final VelocityContext context = new VelocityContext();
        context.put("name", "Texen");

        final String output = generator.parse("control.vm", context);

        assertThat(output).isEqualTo("Generated Texen in " + outputDirectory);
        assertThat(context.get("generator")).isSameAs(Generator.getInstance());
        assertThat(context.get("outputDirectory")).isEqualTo(outputDirectory.toString());
        assertThat(context.get("strings")).isInstanceOf(StringUtils.class);
        assertThat(context.get("files")).isInstanceOf(FileUtil.class);
        assertThat(context.get("properties")).isInstanceOf(PropertiesUtil.class);
    }

    private static void assertExpectedGeneratorLoader(
            final ClassLoader generatorLoader,
            final ClassLoader isolatedLoader
    ) {
        if (isNativeImageRuntime()) {
            assertThat(generatorLoader)
                    .matches(
                            loader -> loader == isolatedLoader || loader == ClassLoader.getSystemClassLoader(),
                            "be the isolated loader or the system loader in native image"
                    );
            return;
        }

        assertThat(generatorLoader).isSameAs(isolatedLoader);
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    private static final class IsolatedGeneratorClassLoader extends ClassLoader {
        private static final String GENERATOR_CLASS_NAME = "org.apache.velocity.texen.Generator";
        private static final String GENERATOR_RESOURCE_NAME = "org/apache/velocity/texen/Generator.class";

        private IsolatedGeneratorClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!GENERATOR_CLASS_NAME.equals(name)) {
                return super.loadClass(name, resolve);
            }

            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null) {
                    loadedClass = defineGeneratorClass();
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private Class<?> defineGeneratorClass() throws ClassNotFoundException {
            try {
                final byte[] classBytes = readGeneratorClassBytes();
                return defineClass(GENERATOR_CLASS_NAME, classBytes, 0, classBytes.length);
            } catch (IOException exception) {
                throw new ClassNotFoundException(GENERATOR_CLASS_NAME, exception);
            }
        }

        private byte[] readGeneratorClassBytes() throws IOException, ClassNotFoundException {
            try (InputStream inputStream = getParent().getResourceAsStream(GENERATOR_RESOURCE_NAME)) {
                if (inputStream == null) {
                    throw new ClassNotFoundException(GENERATOR_CLASS_NAME);
                }
                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                final byte[] buffer = new byte[4096];
                int bytesRead = inputStream.read(buffer);
                while (bytesRead != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    bytesRead = inputStream.read(buffer);
                }
                return outputStream.toByteArray();
            }
        }
    }
}
