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

    private String coordinates;
    private String agentAllowedPackages;

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

    @Option(option = "agentAllowedPackages", description = "Comma separated allowed packages (or - for none)")
    public void setAgentAllowedPackages(String agentAllowedPackages) {
        this.agentAllowedPackages = agentAllowedPackages;
    }

    @Input
    @Optional
    public String getAgentAllowedPackages() {
        return agentAllowedPackages;
    }

    @TaskAction
    public void run() throws IOException {
        Path testsDirectory = GeneralUtils.computeTestsDirectory(getLayout(), coordinates);
        Path gradlewPath = GeneralUtils.getPathFromProject(getLayout(), GRADLEW);
        
        List<String> packageList = (agentAllowedPackages == null || agentAllowedPackages.isBlank() || agentAllowedPackages.equals("-"))
                ? List.of()
                : Arrays.stream(agentAllowedPackages.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());

        if (!packageList.isEmpty()) {
            MetadataGenerationUtils.addUserCodeFilterFile(testsDirectory, packageList);
            MetadataGenerationUtils.addAgentConfigBlock(testsDirectory);
        }
        MetadataGenerationUtils.collectMetadata(getExecOperations(), testsDirectory, getLayout(), coordinates, gradlewPath);
        Path metadataDirectory = GeneralUtils.computeMetadataDirectory(getLayout(), coordinates);
    }
}
