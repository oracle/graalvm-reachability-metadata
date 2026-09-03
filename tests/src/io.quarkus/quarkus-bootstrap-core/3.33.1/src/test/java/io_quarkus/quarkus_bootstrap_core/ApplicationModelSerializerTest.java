/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.bootstrap.app.ApplicationModelSerializer;
import io.quarkus.bootstrap.model.ApplicationModel;
import io.quarkus.bootstrap.model.ApplicationModelBuilder;
import io.quarkus.bootstrap.model.PlatformImportsImpl;
import io.quarkus.maven.dependency.ResolvedDependencyBuilder;

public class ApplicationModelSerializerTest {

    private static final byte[] JAVA_OBJECT_STREAM_MAGIC = {(byte) 0xAC, (byte) 0xED };

    @TempDir
    Path temporaryDirectory;

    @Test
    void serializesAndDeserializesApplicationModelWithJavaObjectSerialization() throws Exception {
        final ApplicationModel model = new ApplicationModelBuilder()
                .setAppArtifact(ResolvedDependencyBuilder.newInstance()
                        .setGroupId("test")
                        .setArtifactId("serialized-application")
                        .setVersion("1.0")
                        .setResolvedPath(temporaryDirectory.resolve("serialized-application.jar")))
                .setPlatformImports(new PlatformImportsImpl())
                .build();
        final Path serializedModel = temporaryDirectory.resolve("application-model.dat");

        ApplicationModelSerializer.serialize(model, serializedModel);
        assertArrayEquals(JAVA_OBJECT_STREAM_MAGIC, firstBytes(serializedModel, JAVA_OBJECT_STREAM_MAGIC.length));

        final ApplicationModel deserialized = ApplicationModelSerializer.deserialize(serializedModel);

        assertNotNull(deserialized);
        assertEquals(model.getAppArtifact(), deserialized.getAppArtifact());
        assertEquals(model.getDependencies(), deserialized.getDependencies());
        assertEquals(model.getPlatforms().getPlatformProperties(), deserialized.getPlatforms().getPlatformProperties());
    }

    private static byte[] firstBytes(Path path, int count) throws Exception {
        return Arrays.copyOf(Files.readAllBytes(path), count);
    }
}
