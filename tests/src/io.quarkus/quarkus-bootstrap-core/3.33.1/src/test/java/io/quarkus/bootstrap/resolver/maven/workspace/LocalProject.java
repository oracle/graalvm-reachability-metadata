/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io.quarkus.bootstrap.resolver.maven.workspace;

import java.nio.file.Path;

import io.quarkus.bootstrap.workspace.WorkspaceModule;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ResolvedDependency;
import io.quarkus.maven.dependency.ResolvedDependencyBuilder;

public final class LocalProject {

    private LocalProject() {
    }

    public static LocalProject loadWorkspace(Path projectRoot) {
        return new LocalProject();
    }

    public ResolvedDependency getAppArtifact(String type) {
        return ResolvedDependencyBuilder.newInstance()
                .setCoords(ArtifactCoords.of("io.quarkus.test", "jbang-resource-smoke-test", null, type, "1.0.0"))
                .build();
    }

    public WorkspaceModule toWorkspaceModule() {
        return null;
    }
}
