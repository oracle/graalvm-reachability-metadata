/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_bootstrap_maven4_resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;

import io.quarkus.bootstrap.resolver.maven.BootstrapArtifactTransformer;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.eclipse.aether.RequestTrace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.metadata.DefaultMetadata;
import org.eclipse.aether.metadata.Metadata;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.spi.artifact.transformer.ArtifactTransformer;
import org.junit.jupiter.api.Test;

public class BootstrapArtifactTransformerTest {
    private static final String SISU_NAMED_INDEX = "META-INF/sisu/javax.inject.Named";
    private static final String BOOTSTRAP_ARTIFACT_TRANSFORMER =
            "io.quarkus.bootstrap.resolver.maven.BootstrapArtifactTransformer";

    @Test
    void transformerIsAvailableAsMavenResolverArtifactTransformer() {
        ArtifactTransformer transformer = new BootstrapArtifactTransformer();

        assertThat(transformer).isNotNull();
    }

    @Test
    void installArtifactsArePassedThroughUnchanged() {
        Artifact artifact = new DefaultArtifact("io.quarkus:test-artifact:jar:1.0.0");
        Metadata metadata = new DefaultMetadata(
                "io.quarkus", "test-artifact", "1.0.0", "maven-metadata.xml", Metadata.Nature.RELEASE);
        RequestTrace trace = new RequestTrace("install-request");
        InstallRequest request = new InstallRequest()
                .addArtifact(artifact)
                .addMetadata(metadata)
                .setTrace(trace);
        ArtifactTransformer transformer = new BootstrapArtifactTransformer();

        InstallRequest transformed = transformer.transformInstallArtifacts(null, request);

        assertSame(request, transformed);
        assertThat(transformed.getArtifacts()).containsExactly(artifact);
        assertThat(transformed.getMetadata()).containsExactly(metadata);
        assertSame(trace, transformed.getTrace());
    }

    @Test
    void deployArtifactsArePassedThroughUnchanged() {
        Artifact artifact = new DefaultArtifact("io.quarkus:test-artifact:jar:sources:1.0.0");
        Metadata metadata = new DefaultMetadata(
                "io.quarkus", "test-artifact", "1.0.0", "maven-metadata.xml", Metadata.Nature.SNAPSHOT);
        RemoteRepository repository = new RemoteRepository.Builder(
                "local-test-repository", "default", "file:///tmp/repo").build();
        RequestTrace trace = new RequestTrace("deploy-request");
        DeployRequest request = new DeployRequest()
                .addArtifact(artifact)
                .addMetadata(metadata)
                .setRepository(repository)
                .setTrace(trace);
        ArtifactTransformer transformer = new BootstrapArtifactTransformer();

        DeployRequest transformed = transformer.transformDeployArtifacts(null, request);

        assertSame(request, transformed);
        assertThat(transformed.getArtifacts()).containsExactly(artifact);
        assertThat(transformed.getMetadata()).containsExactly(metadata);
        assertSame(repository, transformed.getRepository());
        assertSame(trace, transformed.getTrace());
    }

    @Test
    void transformerIsIndexedForSisuNamedComponentDiscovery() throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = classLoader.getResources(SISU_NAMED_INDEX);
        List<String> indexedComponents = new ArrayList<>();
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            try (InputStream input = resource.openStream()) {
                String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                content.lines()
                        .map(String::trim)
                        .filter(line -> !line.isEmpty())
                        .forEach(indexedComponents::add);
            }
        }

        assertThat(indexedComponents).contains(BOOTSTRAP_ARTIFACT_TRANSFORMER);
    }
}
