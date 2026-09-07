/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateNetworkResponse;
import com.github.dockerjava.api.command.CreateVolumeResponse;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.api.model.Version;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class TestcontainersTest {
    private static final boolean DEBUG = false;
    private static final String NGINX_IMAGE = "nginx:1-alpine-slim";

    @BeforeAll
    static void beforeAll() {
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", DEBUG ? "debug" : "warn");
        System.setProperty("api.version", "1.44");
    }

    @Test
    @Timeout(55)
    void exercisesContainerLifecycleAndStreamingCommands() throws Exception {
        byte[] page = "<h1>Testcontainers integration</h1>".getBytes(StandardCharsets.UTF_8);

        try (
            GenericContainer<?> nginx = new GenericContainer<>(NGINX_IMAGE)
                .withExposedPorts(80)
                .withEnv("TESTCONTAINERS_TEST", "active")
                .withLabel("testcontainers.integration", "true")
                .withCopyToContainer(Transferable.of(page, 0644), "/usr/share/nginx/html/index.html")
                .waitingFor(Wait.forHttp("/").forStatusCode(200))
                .withStartupTimeout(Duration.ofSeconds(30))
        ) {
            nginx.start();

            assertThat(nginx.isCreated()).isTrue();
            assertThat(nginx.isRunning()).isTrue();
            assertThat(nginx.getCurrentContainerInfo().getConfig().getEnv())
                .contains("TESTCONTAINERS_TEST=active");
            assertThat(nginx.getExposedPorts()).containsExactly(80);
            assertThat(nginx.getFirstMappedPort()).isPositive();

            URI uri = URI.create(String.format("http://%s:%d", nginx.getHost(), nginx.getFirstMappedPort()));
            try (HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
                HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.body()).isEqualTo(new String(page, StandardCharsets.UTF_8));
            }

            Container.ExecResult execResult = nginx.execInContainer(
                "sh",
                "-c",
                "printf container-output; printf container-error >&2"
            );
            assertThat(execResult.getExitCode()).isZero();
            assertThat(execResult.getStdout()).isEqualTo("container-output");
            assertThat(execResult.getStderr()).isEqualTo("container-error");

            byte[] copied = "copied through the Docker archive API".getBytes(StandardCharsets.UTF_8);
            nginx.copyFileToContainer(Transferable.of(copied, 0644), "/tmp/copied.txt");
            byte[] copiedBack = nginx.copyFileFromContainer("/tmp/copied.txt", input -> input.readAllBytes());
            assertThat(copiedBack).isEqualTo(copied);
            assertThat(nginx.getLogs()).contains("GET / HTTP/1.1");

            DockerClient dockerClient = DockerClientFactory.instance().client();
            String containerId = nginx.getContainerId();
            assertThat(dockerClient.inspectImageCmd(NGINX_IMAGE).exec().getId()).isNotBlank();
            assertThat(dockerClient.listImagesCmd().exec()).isNotEmpty();
            assertThat(dockerClient.listContainersCmd().withShowAll(true).exec())
                .anyMatch(container -> containerId.equals(container.getId()));
            assertThat(dockerClient.topContainerCmd(containerId).exec().getProcesses()).isNotEmpty();
            assertThat(dockerClient.containerDiffCmd(containerId).exec())
                .anyMatch(change -> "/tmp/copied.txt".equals(change.getPath()));

            AtomicReference<Statistics> statistics = new AtomicReference<>();
            try (
                ResultCallback.Adapter<Statistics> callback = new ResultCallback.Adapter<>() {
                    @Override
                    public void onNext(Statistics value) {
                        statistics.set(value);
                    }
                }
            ) {
                dockerClient.statsCmd(containerId).withNoStream(true).exec(callback);
                assertThat(callback.awaitCompletion(10, TimeUnit.SECONDS)).isTrue();
                assertThat(statistics.get()).isNotNull();
                assertThat(statistics.get().getMemoryStats()).isNotNull();
            }

            dockerClient.pauseContainerCmd(containerId).exec();
            assertThat(dockerClient.inspectContainerCmd(containerId).exec().getState().getPaused()).isTrue();
            dockerClient.unpauseContainerCmd(containerId).exec();

            String renamedContainer = "testcontainers-integration-" + UUID.randomUUID();
            dockerClient.renameContainerCmd(containerId).withName(renamedContainer).exec();
            assertThat(dockerClient.inspectContainerCmd(containerId).exec().getName()).endsWith(renamedContainer);

            try (WaitContainerResultCallback callback = dockerClient.waitContainerCmd(containerId).start()) {
                dockerClient.stopContainerCmd(containerId).withTimeout(10).exec();
                assertThat(callback.awaitStatusCode(10, TimeUnit.SECONDS)).isNotNegative();
            }
            assertThat(nginx.isRunning()).isFalse();

            dockerClient.startContainerCmd(containerId).exec();
            assertThat(nginx.isRunning()).isTrue();
        }
    }

    @Test
    @Timeout(55)
    void buildsImageFromInMemoryDockerfile() throws Exception {
        DockerClient dockerClient = DockerClientFactory.instance().client();
        String imageName = "localhost/testcontainers/in-memory-" + UUID.randomUUID();
        String dockerfile = """
            FROM %s
            LABEL testcontainers.feature="image-builder"
            ENV TESTCONTAINERS_BUILT_IMAGE="true"
            """.formatted(NGINX_IMAGE);
        boolean imageBuilt = false;

        try {
            ImageFromDockerfile image = new ImageFromDockerfile(imageName, false)
                .withFileFromString("Dockerfile", dockerfile);

            assertThat(image.get(30, TimeUnit.SECONDS)).isEqualTo(imageName);
            imageBuilt = true;

            InspectImageResponse imageInfo = dockerClient.inspectImageCmd(imageName).exec();
            assertThat(imageInfo.getConfig().getLabels())
                .containsEntry("testcontainers.feature", "image-builder");
            assertThat(imageInfo.getConfig().getEnv()).contains("TESTCONTAINERS_BUILT_IMAGE=true");
        } finally {
            if (imageBuilt) {
                dockerClient.removeImageCmd(imageName).withForce(true).exec();
            }
        }
    }

    @Test
    @Timeout(55)
    void exercisesDaemonNetworkAndVolumeCommands() {
        DockerClient dockerClient = DockerClientFactory.instance().client();
        dockerClient.pingCmd().exec();

        Info info = dockerClient.infoCmd().exec();
        Version version = dockerClient.versionCmd().exec();
        assertThat(info.getServerVersion()).isNotBlank();
        assertThat(version.getApiVersion()).isNotBlank();
        assertThat(DockerClientFactory.instance().getActiveApiVersion()).isEqualTo(version.getApiVersion());

        String suffix = UUID.randomUUID().toString();
        String networkName = "testcontainers-network-" + suffix;
        String volumeName = "testcontainers-volume-" + suffix;
        String networkId = null;
        boolean volumeCreated = false;

        try {
            CreateNetworkResponse network = dockerClient
                .createNetworkCmd()
                .withName(networkName)
                .withDriver("bridge")
                .withCheckDuplicate(true)
                .withLabels(Map.of("testcontainers.integration", "network"))
                .exec();
            networkId = network.getId();
            assertThat(networkId).isNotBlank();
            assertThat(dockerClient.inspectNetworkCmd().withNetworkId(networkId).exec().getName())
                .isEqualTo(networkName);
            String createdNetworkId = networkId;
            assertThat(dockerClient.listNetworksCmd().withNameFilter(networkName).exec())
                .anyMatch(candidate -> createdNetworkId.equals(candidate.getId()));

            CreateVolumeResponse volume = dockerClient
                .createVolumeCmd()
                .withName(volumeName)
                .withDriver("local")
                .withLabels(Map.of("testcontainers.integration", "volume"))
                .exec();
            volumeCreated = true;
            assertThat(volume.getName()).isEqualTo(volumeName);
            assertThat(dockerClient.inspectVolumeCmd(volumeName).exec().getLabels())
                .containsEntry("testcontainers.integration", "volume");
            assertThat(dockerClient.listVolumesCmd().exec().getVolumes())
                .anyMatch(candidate -> volumeName.equals(candidate.getName()));
        } finally {
            try {
                if (volumeCreated) {
                    dockerClient.removeVolumeCmd(volumeName).exec();
                }
            } finally {
                if (networkId != null) {
                    dockerClient.removeNetworkCmd(networkId).exec();
                }
            }
        }
    }
}
