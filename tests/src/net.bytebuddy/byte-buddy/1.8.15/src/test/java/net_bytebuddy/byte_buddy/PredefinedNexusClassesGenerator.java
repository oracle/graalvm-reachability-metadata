/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class PredefinedNexusClassesGenerator {
    private PredefinedNexusClassesGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single output directory argument");
        }

        Path outputDirectory = Path.of(args[0]).toAbsolutePath().normalize();
        writePredefinedClasses(outputDirectory);
        verifyGeneratedClasses(outputDirectory);
    }

    private static void writePredefinedClasses(Path outputDirectory) throws IOException {
        writePredefinedClass(
                outputDirectory,
                NexusInitializationSupport.GENERATED_TYPE_NAME,
                NexusInitializationSupport.generatePredefinedClassBytes());
        writePredefinedClass(
                outputDirectory,
                GeneratedTypeFixtures.STATIC_FIELD_CALLABLE,
                GeneratedTypeFixtures.staticFieldCallable(new Object()).getBytes());
        writePredefinedClasses(
                outputDirectory,
                GeneratedTypeFixtures.agentBuilderTypeDefinitions(
                        GeneratedTypeFixtures.LOCK_HELD_SUPER_TYPE,
                        GeneratedTypeFixtures.LOCK_HELD_SUB_TYPE));
        writePredefinedClasses(
                outputDirectory,
                GeneratedTypeFixtures.agentBuilderTypeDefinitions(
                        GeneratedTypeFixtures.SIMPLE_ACTION_SUPER_TYPE,
                        GeneratedTypeFixtures.SIMPLE_ACTION_SUB_TYPE));
    }

    private static void writePredefinedClasses(Path outputDirectory, Map<String, byte[]> classFiles) throws IOException {
        for (Map.Entry<String, byte[]> entry : classFiles.entrySet()) {
            writePredefinedClass(outputDirectory, entry.getKey(), entry.getValue());
        }
    }

    private static void writePredefinedClass(Path outputDirectory, String typeName, byte[] classBytes) throws IOException {
        Path generatedClass = generatedClassFile(outputDirectory, typeName);
        Files.createDirectories(generatedClass.getParent());
        Files.write(generatedClass, classBytes);
    }

    private static void verifyGeneratedClasses(Path outputDirectory) throws IOException {
        verifyGeneratedClass(outputDirectory, NexusInitializationSupport.GENERATED_TYPE_NAME);
        verifyGeneratedClass(outputDirectory, GeneratedTypeFixtures.STATIC_FIELD_CALLABLE);
        verifyGeneratedClass(outputDirectory, GeneratedTypeFixtures.LOCK_HELD_SUPER_TYPE);
        verifyGeneratedClass(outputDirectory, GeneratedTypeFixtures.LOCK_HELD_SUB_TYPE);
        verifyGeneratedClass(outputDirectory, GeneratedTypeFixtures.SIMPLE_ACTION_SUPER_TYPE);
        verifyGeneratedClass(outputDirectory, GeneratedTypeFixtures.SIMPLE_ACTION_SUB_TYPE);
    }

    private static void verifyGeneratedClass(Path outputDirectory, String typeName) throws IOException {
        Path generatedClass = generatedClassFile(outputDirectory, typeName);
        if (!Files.isRegularFile(generatedClass)) {
            throw new IOException("Missing generated predefined class: " + generatedClass);
        }
    }

    private static Path generatedClassFile(Path outputDirectory, String typeName) {
        return outputDirectory.resolve(typeName.replace('.', '/') + ".class");
    }
}
