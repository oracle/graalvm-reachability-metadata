/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_app_model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkus.bootstrap.model.ApplicationModel;
import io.quarkus.bootstrap.model.ApplicationModelBuilder;
import io.quarkus.bootstrap.util.BootstrapUtils;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ResolvedDependencyBuilder;
import io.quarkus.paths.PathList;

public class BootstrapUtilsTest {
    private static final String APP_COORDINATES = "org.example:bootstrap-utils-test-app::jar:test-version";

    @TempDir
    Path tempDir;

    @Test
    @SuppressWarnings("removal")
    public void serializesAndDeserializesQuarkusModel() throws Exception {
        final ApplicationModel model = newApplicationModel();
        final Path modelFile = tempDir.resolve("plain").resolve("app-model.dat");

        BootstrapUtils.serializeAppModel(model, modelFile);

        assertTrue(Files.exists(modelFile));

        final ApplicationModel copy = BootstrapUtils.deserializeQuarkusModel(modelFile);

        assertSameApplicationArtifact(model, copy);
        assertFalse(Files.exists(modelFile));
    }

    @Test
    @SuppressWarnings("removal")
    public void writesAndReadsAppModelWithWorkspaceId() throws Exception {
        final ApplicationModel model = newApplicationModel();
        final Path modelFile = tempDir.resolve("workspace").resolve("app-model.dat");
        final int workspaceId = 42;

        BootstrapUtils.writeAppModelWithWorkspaceId(model, workspaceId, modelFile);

        assertTrue(Files.exists(modelFile));

        final ApplicationModel copy = BootstrapUtils.readAppModelWithWorkspaceId(modelFile, workspaceId);

        assertSameApplicationArtifact(model, copy);
    }

    private static ApplicationModel newApplicationModel() {
        return new ApplicationModelBuilder()
                .setAppArtifact(ResolvedDependencyBuilder.newInstance()
                        .setCoords(ArtifactCoords.fromString(APP_COORDINATES))
                        .setResolvedPaths(PathList.empty()))
                .build();
    }

    private static void assertSameApplicationArtifact(final ApplicationModel expected, final ApplicationModel actual) {
        assertNotNull(actual);
        assertEquals(expected.getAppArtifact().toGACTVString(), actual.getAppArtifact().toGACTVString());
        assertEquals(0, actual.getDependencies().size());
    }
}
