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

public final class PredefinedNexusClassesGenerator {
    private PredefinedNexusClassesGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single output directory argument");
        }

        Path outputDirectory = Path.of(args[0]).toAbsolutePath().normalize();
        writePredefinedClass(outputDirectory);
        verifyGeneratedClass(outputDirectory);
    }

    private static void writePredefinedClass(Path outputDirectory) throws IOException {
        Path generatedClass = outputDirectory.resolve(
                NexusInitializationSupport.GENERATED_TYPE_NAME.replace('.', '/') + ".class");
        Files.createDirectories(generatedClass.getParent());
        Files.write(generatedClass, NexusInitializationSupport.generatePredefinedClassBytes());
    }

    private static void verifyGeneratedClass(Path outputDirectory) throws IOException {
        Path generatedClass = outputDirectory.resolve(
                NexusInitializationSupport.GENERATED_TYPE_NAME.replace('.', '/') + ".class");
        if (!Files.isRegularFile(generatedClass)) {
            throw new IOException("Missing generated Nexus predefined class: " + generatedClass);
        }
    }
}
