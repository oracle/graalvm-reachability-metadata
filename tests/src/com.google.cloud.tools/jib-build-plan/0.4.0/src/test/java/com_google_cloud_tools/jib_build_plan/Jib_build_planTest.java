/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud_tools.jib_build_plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.ContainerBuildPlan;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FileEntry;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.api.buildplan.ImageFormat;
import com.google.cloud.tools.jib.api.buildplan.LayerObject;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.cloud.tools.jib.api.buildplan.RelativeUnixPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Jib_build_planTest {
    @Test
    void defaultContainerBuildPlanUsesExpectedDefaults() {
        ContainerBuildPlan plan = ContainerBuildPlan.builder().build();

        assertThat(plan.getBaseImage()).isEqualTo("scratch");
        assertThat(plan.getPlatforms()).containsExactly(new Platform("amd64", "linux"));
        assertThat(plan.getFormat()).isEqualTo(ImageFormat.Docker);
        assertThat(plan.getCreationTime()).isEqualTo(Instant.EPOCH);
        assertThat(plan.getEnvironment()).isEmpty();
        assertThat(plan.getVolumes()).isEmpty();
        assertThat(plan.getLabels()).isEmpty();
        assertThat(plan.getExposedPorts()).isEmpty();
        assertThat(plan.getUser()).isNull();
        assertThat(plan.getWorkingDirectory()).isNull();
        assertThat(plan.getEntrypoint()).isNull();
        assertThat(plan.getCmd()).isNull();
        assertThat(plan.getLayers()).isEmpty();

        assertThatThrownBy(() -> ContainerBuildPlan.builder().setPlatforms(Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("platforms set cannot be empty");
    }

    @Test
    void containerBuildPlanCapturesMutableInputsAndSupportsToBuilder() {
        Set<Platform> platforms = new LinkedHashSet<>();
        platforms.add(new Platform("arm64", "linux"));
        platforms.add(new Platform("amd64", "linux"));

        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("JAVA_TOOL_OPTIONS", "-Xmx256m");

        Set<AbsoluteUnixPath> volumes = new LinkedHashSet<>();
        volumes.add(AbsoluteUnixPath.get("/cache"));

        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("org.opencontainers.image.title", "demo-app");

        Set<Port> ports = new LinkedHashSet<>();
        ports.add(Port.tcp(8080));

        List<String> entrypoint = new ArrayList<>(List.of("java", "-jar", "/app/app.jar"));
        List<String> cmd = new ArrayList<>(List.of("--debug"));

        FileEntriesLayer dependenciesLayer = FileEntriesLayer.builder().setName("dependencies").build();
        List<LayerObject> layers = new ArrayList<>();
        layers.add(dependenciesLayer);

        ContainerBuildPlan plan = ContainerBuildPlan.builder()
                .setBaseImage("eclipse-temurin:21-jre")
                .setPlatforms(platforms)
                .setFormat(ImageFormat.OCI)
                .setCreationTime(Instant.parse("2024-01-02T03:04:05Z"))
                .setEnvironment(environment)
                .setVolumes(volumes)
                .setLabels(labels)
                .setExposedPorts(ports)
                .setUser("1000:1000")
                .setWorkingDirectory(AbsoluteUnixPath.get("/workspace"))
                .setEntrypoint(entrypoint)
                .setCmd(cmd)
                .setLayers(layers)
                .build();

        platforms.clear();
        environment.put("SHOULD_NOT_LEAK", "true");
        volumes.add(AbsoluteUnixPath.get("/tmp"));
        labels.put("mutated", "true");
        ports.add(Port.udp(5353));
        entrypoint.add("mutated");
        cmd.add("--trace");
        layers.clear();

        assertThat(plan.getBaseImage()).isEqualTo("eclipse-temurin:21-jre");
        assertThat(plan.getPlatforms()).containsExactly(new Platform("arm64", "linux"), new Platform("amd64", "linux"));
        assertThat(plan.getFormat()).isEqualTo(ImageFormat.OCI);
        assertThat(plan.getCreationTime()).isEqualTo(Instant.parse("2024-01-02T03:04:05Z"));
        assertThat(plan.getEnvironment()).containsOnly(Map.entry("JAVA_TOOL_OPTIONS", "-Xmx256m"));
        assertThat(plan.getVolumes()).containsExactly(AbsoluteUnixPath.get("/cache"));
        assertThat(plan.getLabels()).containsOnly(Map.entry("org.opencontainers.image.title", "demo-app"));
        assertThat(plan.getExposedPorts()).containsExactly(Port.tcp(8080));
        assertThat(plan.getUser()).isEqualTo("1000:1000");
        assertThat(plan.getWorkingDirectory()).isEqualTo(AbsoluteUnixPath.get("/workspace"));
        assertThat(plan.getEntrypoint()).containsExactly("java", "-jar", "/app/app.jar");
        assertThat(plan.getCmd()).containsExactly("--debug");
        assertThat(plan.getLayers()).hasSize(1);
        assertThat(plan.getLayers().get(0)).isSameAs(dependenciesLayer);

        Map<String, String> environmentCopy = plan.getEnvironment();
        environmentCopy.put("EXTRA", "value");
        Set<AbsoluteUnixPath> volumeCopy = plan.getVolumes();
        volumeCopy.add(AbsoluteUnixPath.get("/logs"));
        List<String> entrypointCopy = plan.getEntrypoint();
        entrypointCopy.add("changed");
        List<? extends LayerObject> layersCopy = plan.getLayers();
        layersCopy.clear();

        assertThat(plan.getEnvironment()).containsOnly(Map.entry("JAVA_TOOL_OPTIONS", "-Xmx256m"));
        assertThat(plan.getVolumes()).containsExactly(AbsoluteUnixPath.get("/cache"));
        assertThat(plan.getEntrypoint()).containsExactly("java", "-jar", "/app/app.jar");
        assertThat(plan.getLayers()).hasSize(1);
        assertThat(plan.getLayers().get(0)).isSameAs(dependenciesLayer);

        ContainerBuildPlan updatedPlan = plan.toBuilder()
                .addEnvironmentVariable("SPRING_PROFILES_ACTIVE", "native")
                .addLabel("updated", "true")
                .addVolume(AbsoluteUnixPath.get("/logs"))
                .addExposedPort(Port.udp(5353))
                .build();

        assertThat(updatedPlan.getEnvironment()).containsOnly(
                Map.entry("JAVA_TOOL_OPTIONS", "-Xmx256m"),
                Map.entry("SPRING_PROFILES_ACTIVE", "native"));
        assertThat(updatedPlan.getLabels()).containsOnly(
                Map.entry("org.opencontainers.image.title", "demo-app"),
                Map.entry("updated", "true"));
        assertThat(updatedPlan.getVolumes()).containsExactlyInAnyOrder(
                AbsoluteUnixPath.get("/cache"),
                AbsoluteUnixPath.get("/logs"));
        assertThat(updatedPlan.getExposedPorts()).containsExactlyInAnyOrder(Port.tcp(8080), Port.udp(5353));
        assertThat(plan.getEnvironment()).containsOnly(Map.entry("JAVA_TOOL_OPTIONS", "-Xmx256m"));
        assertThat(plan.getLabels()).containsOnly(Map.entry("org.opencontainers.image.title", "demo-app"));
        assertThat(plan.getVolumes()).containsExactly(AbsoluteUnixPath.get("/cache"));
        assertThat(plan.getExposedPorts()).containsExactly(Port.tcp(8080));
    }

    @Test
    void unixPathsResolveAndValidateInputs() {
        AbsoluteUnixPath normalizedPath = AbsoluteUnixPath.get("/workspace//app/config");
        AbsoluteUnixPath resolvedFromRelativeObject = normalizedPath.resolve(RelativeUnixPath.get("lib/runtime"));
        AbsoluteUnixPath resolvedFromRelativeString = normalizedPath.resolve("bin/launcher");
        AbsoluteUnixPath resolvedFromPath = normalizedPath.resolve(Paths.get("scripts/../start.sh"));
        AbsoluteUnixPath fromPath = AbsoluteUnixPath.fromPath(Paths.get("/opt/jib/app"));

        assertThat(normalizedPath).isEqualTo(AbsoluteUnixPath.get("/workspace/app/config"));
        assertThat(resolvedFromRelativeObject).isEqualTo(AbsoluteUnixPath.get("/workspace/app/config/lib/runtime"));
        assertThat(resolvedFromRelativeString).isEqualTo(AbsoluteUnixPath.get("/workspace/app/config/bin/launcher"));
        assertThat(resolvedFromPath).isEqualTo(AbsoluteUnixPath.get("/workspace/app/config/scripts/../start.sh"));
        assertThat(fromPath).isEqualTo(AbsoluteUnixPath.get("/opt/jib/app"));
        assertThat(normalizedPath.toString()).isEqualTo("/workspace/app/config");

        assertThatThrownBy(() -> AbsoluteUnixPath.get("workspace/app"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not start with forward slash");
        assertThatThrownBy(() -> RelativeUnixPath.get("/workspace/app"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("starts with forward slash");
        assertThatThrownBy(() -> AbsoluteUnixPath.fromPath(Paths.get("relative/path")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-absolute Path");
        assertThatThrownBy(() -> normalizedPath.resolve(Paths.get("/already/absolute")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute Path");
    }

    @Test
    void filePermissionsPortsAndPlatformsExposeValueSemantics() {
        FilePermissions octalPermissions = FilePermissions.fromOctalString("750");
        FilePermissions posixPermissions = FilePermissions.fromPosixFilePermissions(
                PosixFilePermissions.fromString("rwxr-x---"));
        Platform arm64Linux = new Platform("arm64", "linux");
        Platform sameArm64Linux = new Platform("arm64", "linux");
        Port tcpPort = Port.tcp(8080);
        Port udpPort = Port.udp(53);
        Port parsedUdpPort = Port.parseProtocol(53, "UDP");
        Port parsedFallbackTcpPort = Port.parseProtocol(8080, "http");

        assertThat(FilePermissions.DEFAULT_FILE_PERMISSIONS.toOctalString()).isEqualTo("644");
        assertThat(FilePermissions.DEFAULT_FOLDER_PERMISSIONS.toOctalString()).isEqualTo("755");
        assertThat(octalPermissions.getPermissionBits()).isEqualTo(488);
        assertThat(octalPermissions.toOctalString()).isEqualTo("750");
        assertThat(posixPermissions).isEqualTo(octalPermissions);
        assertThat(posixPermissions.hashCode()).isEqualTo(octalPermissions.hashCode());

        assertThatThrownBy(() -> FilePermissions.fromOctalString("888"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3-digit octal number");

        assertThat(arm64Linux.getArchitecture()).isEqualTo("arm64");
        assertThat(arm64Linux.getOs()).isEqualTo("linux");
        assertThat(arm64Linux).isEqualTo(sameArm64Linux);
        assertThat(arm64Linux.hashCode()).isEqualTo(sameArm64Linux.hashCode());
        assertThat(arm64Linux).isNotEqualTo(new Platform("amd64", "linux"));

        assertThat(tcpPort.getPort()).isEqualTo(8080);
        assertThat(tcpPort.getProtocol()).isEqualTo("tcp");
        assertThat(tcpPort.toString()).isEqualTo("8080/tcp");
        assertThat(udpPort).isEqualTo(parsedUdpPort);
        assertThat(udpPort.hashCode()).isEqualTo(parsedUdpPort.hashCode());
        assertThat(parsedFallbackTcpPort).isEqualTo(tcpPort);
        assertThat(udpPort).isNotEqualTo(tcpPort);
    }

    @Test
    void fileEntriesLayerSupportsExplicitEntriesAndRoundTrips(@TempDir Path tempDir) throws Exception {
        Path sourceFile = Files.writeString(tempDir.resolve("source.txt"), "hello jib");
        Instant modificationTime = Instant.parse("2024-02-03T04:05:06Z");
        FilePermissions readOnlyPermissions = FilePermissions.fromOctalString("640");
        FilePermissions executablePermissions = FilePermissions.fromOctalString("755");
        FileEntry explicitEntry = new FileEntry(
                sourceFile,
                AbsoluteUnixPath.get("/app/source.txt"),
                readOnlyPermissions,
                modificationTime);
        FileEntry sameExplicitEntry = new FileEntry(
                sourceFile,
                AbsoluteUnixPath.get("/app/source.txt"),
                readOnlyPermissions,
                modificationTime);
        FileEntry ownedEntry = new FileEntry(
                sourceFile,
                AbsoluteUnixPath.get("/app/source.txt"),
                readOnlyPermissions,
                modificationTime,
                "1000:1000");

        FileEntriesLayer layer = FileEntriesLayer.builder()
                .setName("resources")
                .addEntry(explicitEntry)
                .addEntry(sourceFile, AbsoluteUnixPath.get("/app/default-mod-time.txt"), readOnlyPermissions)
                .addEntry(
                        sourceFile,
                        AbsoluteUnixPath.get("/app/run.sh"),
                        executablePermissions,
                        modificationTime.plusSeconds(30),
                        "2000:3000")
                .build();

        assertThat(explicitEntry.getSourceFile()).isEqualTo(sourceFile);
        assertThat(explicitEntry.getExtractionPath()).isEqualTo(AbsoluteUnixPath.get("/app/source.txt"));
        assertThat(explicitEntry.getPermissions()).isEqualTo(readOnlyPermissions);
        assertThat(explicitEntry.getModificationTime()).isEqualTo(modificationTime);
        assertThat(explicitEntry.getOwnership()).isEmpty();
        assertThat(explicitEntry).isEqualTo(sameExplicitEntry);
        assertThat(explicitEntry.hashCode()).isEqualTo(sameExplicitEntry.hashCode());
        assertThat(explicitEntry).isNotEqualTo(ownedEntry);

        assertThat(layer.getType()).isEqualTo(LayerObject.Type.FILE_ENTRIES);
        assertThat(layer.getName()).isEqualTo("resources");
        assertThat(layer.getEntries()).hasSize(3);
        assertThat(layer.getEntries().get(0)).isEqualTo(explicitEntry);
        assertThat(layer.getEntries().get(1).getExtractionPath())
                .isEqualTo(AbsoluteUnixPath.get("/app/default-mod-time.txt"));
        assertThat(layer.getEntries().get(1).getPermissions()).isEqualTo(readOnlyPermissions);
        assertThat(layer.getEntries().get(1).getModificationTime())
                .isEqualTo(FileEntriesLayer.DEFAULT_MODIFICATION_TIME);
        assertThat(layer.getEntries().get(1).getOwnership()).isEmpty();
        assertThat(layer.getEntries().get(2).getExtractionPath()).isEqualTo(AbsoluteUnixPath.get("/app/run.sh"));
        assertThat(layer.getEntries().get(2).getPermissions()).isEqualTo(executablePermissions);
        assertThat(layer.getEntries().get(2).getModificationTime())
                .isEqualTo(modificationTime.plusSeconds(30));
        assertThat(layer.getEntries().get(2).getOwnership()).isEqualTo("2000:3000");

        List<FileEntry> entriesCopy = layer.getEntries();
        entriesCopy.clear();
        assertThat(layer.getEntries()).hasSize(3);

        FileEntriesLayer rebuiltLayer = layer.toBuilder().setName("single-entry").setEntries(List.of(ownedEntry)).build();

        assertThat(rebuiltLayer.getName()).isEqualTo("single-entry");
        assertThat(rebuiltLayer.getEntries()).containsExactly(ownedEntry);
        assertThat(layer.getName()).isEqualTo("resources");
        assertThat(layer.getEntries()).hasSize(3);
    }

    @Test
    void fileEntriesLayerAppliesDefaultPermissionsAndTimestampsForDirectEntries(@TempDir Path tempDir)
            throws Exception {
        Path configDirectory = Files.createDirectories(tempDir.resolve("config"));
        Path applicationFile = Files.writeString(tempDir.resolve("application.properties"), "jib.enabled=true\n");
        Instant customModificationTime = Instant.parse("2024-04-05T06:07:08Z");

        FileEntriesLayer layer = FileEntriesLayer.builder()
                .setName("defaults")
                .addEntry(configDirectory, AbsoluteUnixPath.get("/app/config"))
                .addEntry(applicationFile, AbsoluteUnixPath.get("/app/application.properties"), customModificationTime)
                .build();

        assertThat(layer.getEntries()).hasSize(2);

        FileEntry directoryEntry = layer.getEntries().get(0);
        assertThat(directoryEntry.getSourceFile()).isEqualTo(configDirectory);
        assertThat(directoryEntry.getExtractionPath()).isEqualTo(AbsoluteUnixPath.get("/app/config"));
        assertThat(directoryEntry.getPermissions()).isEqualTo(FilePermissions.DEFAULT_FOLDER_PERMISSIONS);
        assertThat(directoryEntry.getModificationTime()).isEqualTo(FileEntriesLayer.DEFAULT_MODIFICATION_TIME);
        assertThat(directoryEntry.getOwnership()).isEmpty();

        FileEntry fileEntry = layer.getEntries().get(1);
        assertThat(fileEntry.getSourceFile()).isEqualTo(applicationFile);
        assertThat(fileEntry.getExtractionPath()).isEqualTo(AbsoluteUnixPath.get("/app/application.properties"));
        assertThat(fileEntry.getPermissions()).isEqualTo(FilePermissions.DEFAULT_FILE_PERMISSIONS);
        assertThat(fileEntry.getModificationTime()).isEqualTo(customModificationTime);
        assertThat(fileEntry.getOwnership()).isEmpty();
    }

    @Test
    void fileEntriesLayerRecursivelyAddsDirectoryContentsWithCustomProviders(@TempDir Path tempDir) throws Exception {
        Path sourceRoot = Files.createDirectories(tempDir.resolve("input"));
        Path configDirectory = Files.createDirectories(sourceRoot.resolve("config"));
        Path launcherScript = Files.writeString(sourceRoot.resolve("launcher.sh"), "#!/bin/sh\nexit 0\n");
        Path configFile = Files.writeString(configDirectory.resolve("application.yaml"), "jib: true\n");
        Instant rootModificationTime = Instant.parse("2024-03-01T00:00:00Z");
        Instant directoryModificationTime = rootModificationTime.plusSeconds(10);
        Instant fileModificationTime = rootModificationTime.plusSeconds(20);
        FilePermissions configFilePermissions = FilePermissions.fromOctalString("640");
        FilePermissions launcherPermissions = FilePermissions.fromOctalString("755");

        FileEntriesLayer layer = FileEntriesLayer.builder()
                .setName("recursive")
                .addEntryRecursive(
                        sourceRoot,
                        AbsoluteUnixPath.get("/app"),
                        (sourcePath, extractionPath) -> {
                            if (Files.isDirectory(sourcePath)) {
                                return FilePermissions.DEFAULT_FOLDER_PERMISSIONS;
                            }
                            return extractionPath.toString().endsWith(".sh")
                                    ? launcherPermissions
                                    : configFilePermissions;
                        },
                        (sourcePath, extractionPath) -> {
                            if (sourcePath.equals(sourceRoot)) {
                                return rootModificationTime;
                            }
                            return Files.isDirectory(sourcePath)
                                    ? directoryModificationTime
                                    : fileModificationTime;
                        },
                        (sourcePath, extractionPath) -> Files.isDirectory(sourcePath) ? "0:0" : "1000:1000")
                .build();

        Map<String, FileEntry> entriesByExtractionPath = new LinkedHashMap<>();
        for (FileEntry entry : layer.getEntries()) {
            entriesByExtractionPath.put(entry.getExtractionPath().toString(), entry);
        }

        assertThat(entriesByExtractionPath.keySet()).containsExactlyInAnyOrder(
                "/app",
                "/app/config",
                "/app/config/application.yaml",
                "/app/launcher.sh");

        assertThat(entriesByExtractionPath.get("/app").getSourceFile()).isEqualTo(sourceRoot);
        assertThat(entriesByExtractionPath.get("/app").getPermissions())
                .isEqualTo(FilePermissions.DEFAULT_FOLDER_PERMISSIONS);
        assertThat(entriesByExtractionPath.get("/app").getModificationTime()).isEqualTo(rootModificationTime);
        assertThat(entriesByExtractionPath.get("/app").getOwnership()).isEqualTo("0:0");

        assertThat(entriesByExtractionPath.get("/app/config").getSourceFile()).isEqualTo(configDirectory);
        assertThat(entriesByExtractionPath.get("/app/config").getPermissions())
                .isEqualTo(FilePermissions.DEFAULT_FOLDER_PERMISSIONS);
        assertThat(entriesByExtractionPath.get("/app/config").getModificationTime())
                .isEqualTo(directoryModificationTime);
        assertThat(entriesByExtractionPath.get("/app/config").getOwnership()).isEqualTo("0:0");

        assertThat(entriesByExtractionPath.get("/app/config/application.yaml").getSourceFile()).isEqualTo(configFile);
        assertThat(entriesByExtractionPath.get("/app/config/application.yaml").getPermissions())
                .isEqualTo(configFilePermissions);
        assertThat(entriesByExtractionPath.get("/app/config/application.yaml").getModificationTime())
                .isEqualTo(fileModificationTime);
        assertThat(entriesByExtractionPath.get("/app/config/application.yaml").getOwnership())
                .isEqualTo("1000:1000");

        assertThat(entriesByExtractionPath.get("/app/launcher.sh").getSourceFile()).isEqualTo(launcherScript);
        assertThat(entriesByExtractionPath.get("/app/launcher.sh").getPermissions()).isEqualTo(launcherPermissions);
        assertThat(entriesByExtractionPath.get("/app/launcher.sh").getModificationTime())
                .isEqualTo(fileModificationTime);
        assertThat(entriesByExtractionPath.get("/app/launcher.sh").getOwnership()).isEqualTo("1000:1000");
    }
}
