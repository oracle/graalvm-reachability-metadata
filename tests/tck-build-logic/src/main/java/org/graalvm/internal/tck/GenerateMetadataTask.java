/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck;

import org.graalvm.internal.tck.utils.MetadataGenerationUtils;
import org.graalvm.internal.tck.utils.GeneralUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates metadata for the given library coordinates and, if packages list is provided, creates a new user-code-filter.json.
 */
public abstract class GenerateMetadataTask extends DefaultTask {
    private static final String GRADLEW = "gradlew";
    private static final String FROM_JAR = "fromJar";

    private String coordinates;
    private String agentAllowedPackages;
    private String metadataOutputDir;

    {
        Object coordsProp = getProject().findProperty("coordinates");
        if ((this.coordinates == null || this.coordinates.isBlank()) && coordsProp != null) {
            this.coordinates = coordsProp.toString();
        }
    }

    @Inject
    protected abstract ProjectLayout getLayout();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Option(option = "coordinates", description = "Coordinates in the form of group:artifact:version")
    public void setCoordinates(String coordinates) {
        this.coordinates = coordinates;
    }

    @Input
    public String getCoordinates() {
        return coordinates;
    }

    @Option(option = "agentAllowedPackages", description = "Comma separated allowed packages, fromJar, or - for none")
    public void setAgentAllowedPackages(String agentAllowedPackages) {
        this.agentAllowedPackages = agentAllowedPackages;
    }

    @Input
    @Optional
    public String getAgentAllowedPackages() {
        return agentAllowedPackages;
    }

    @Option(option = "metadataOutputDir", description = "Directory where generated agent metadata is copied without durable final merge")
    public void setMetadataOutputDir(String metadataOutputDir) {
        this.metadataOutputDir = metadataOutputDir;
    }

    @Input
    @Optional
    public String getMetadataOutputDir() {
        return metadataOutputDir;
    }

    @TaskAction
    public void run() throws IOException {
        Path testsDirectory = GeneralUtils.computeTestsDirectory(getLayout(), coordinates);
        Path gradlewPath = GeneralUtils.getPathFromProject(getLayout(), GRADLEW);
        Coordinates coordinatesValue = Coordinates.parse(coordinates);
        List<String> packageList = resolveAllowedPackages(coordinatesValue);

        if (!packageList.isEmpty()) {
            MetadataGenerationUtils.addUserCodeFilterFile(testsDirectory, packageList);
            MetadataGenerationUtils.addAgentConfigBlock(testsDirectory);
        } else if (!MetadataGenerationUtils.hasAgentConfigBlock(testsDirectory)) {
            // Fresh scaffolds have no agent block; default to library group package so conditional metadata can be generated.
            MetadataGenerationUtils.addUserCodeFilterFile(testsDirectory, List.of(coordinatesValue.group()));
            MetadataGenerationUtils.addAgentConfigBlock(testsDirectory);
        }
        if (metadataOutputDir == null || metadataOutputDir.isBlank()) {
            MetadataGenerationUtils.collectMetadata(getExecOperations(), testsDirectory, getLayout(), coordinates, gradlewPath);
        } else {
            MetadataGenerationUtils.collectMetadata(
                    getExecOperations(),
                    testsDirectory,
                    getLayout(),
                    coordinates,
                    gradlewPath,
                    Path.of(metadataOutputDir)
            );
        }
        if (isFromJarAllowedPackages()) {
            MetadataGenerationUtils.setAllowedPackagesInIndexJson(getLayout(), coordinatesValue, packageList);
        }
    }

    private List<String> resolveAllowedPackages(Coordinates coordinatesValue) throws IOException {
        if (agentAllowedPackages == null || agentAllowedPackages.isBlank() || agentAllowedPackages.equals("-")) {
            return List.of();
        }

        String normalizedValue = agentAllowedPackages.trim();
        if (normalizedValue.equals(FROM_JAR)) {
            return MetadataGenerationUtils.derivePackageRootsFromJar(getProject(), coordinatesValue);
        }

        List<String> packageList = Arrays.stream(agentAllowedPackages.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        if (packageList.contains(FROM_JAR)) {
            throw new IllegalArgumentException("--agentAllowedPackages=fromJar must be used on its own");
        }
        return packageList;
    }

    private boolean isFromJarAllowedPackages() {
        return agentAllowedPackages != null && agentAllowedPackages.trim().equals(FROM_JAR);
    }
}
