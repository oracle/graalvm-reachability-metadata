/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.bootstrap.app.ApplicationModelSerializer;
import io.quarkus.bootstrap.model.ApplicationModel;
import io.quarkus.bootstrap.model.ApplicationModelBuilder;
import io.quarkus.maven.dependency.ResolvedDependency;
import io.quarkus.maven.dependency.ResolvedDependencyBuilder;
import io.quarkus.paths.PathList;

public class ApplicationModelSerializerTest {

    private static final String SERIALIZATION_FORMAT_PROPERTY =
            "quarkus.bootstrap.application-model.serialization.format";

    static {
        System.setProperty(SERIALIZATION_FORMAT_PROPERTY, "jos");
    }

    @TempDir
    Path applicationRoot;

    @Test
    void serializesAndDeserializesApplicationModelWithJavaObjectSerialization() throws Exception {
        ApplicationModel model = applicationModel();
        Path serializedModel = applicationRoot.resolve("application-model.dat");

        ApplicationModelSerializer.serialize(model, serializedModel);
        ApplicationModel deserialized = ApplicationModelSerializer.deserialize(serializedModel);

        assertThat(Files.readAllBytes(serializedModel)).startsWith((byte) 0xAC, (byte) 0xED);
        assertThat(deserialized.getAppArtifact().toGACTVString())
                .isEqualTo("io.quarkus.test:application::jar:1.0");

        List<ResolvedDependency> runtimeDependencies = new ArrayList<>(deserialized.getRuntimeDependencies());
        assertThat(runtimeDependencies).hasSize(1);
        assertThat(runtimeDependencies.get(0).toGACTVString())
                .isEqualTo("io.quarkus.test:runtime-dependency::jar:1.0");
    }

    private static ApplicationModel applicationModel() {
        return new ApplicationModelBuilder()
                .setAppArtifact(dependency("application"))
                .addDependency(dependency("runtime-dependency"))
                .build();
    }

    private static ResolvedDependencyBuilder dependency(String artifactId) {
        ResolvedDependencyBuilder dependency = ResolvedDependencyBuilder.newInstance()
                .setGroupId("io.quarkus.test")
                .setArtifactId(artifactId)
                .setVersion("1.0")
                .setResolvedPaths(PathList.empty());
        dependency.setRuntimeCp();
        return dependency;
    }
}
