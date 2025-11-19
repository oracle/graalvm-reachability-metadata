package org.graalvm.internal.tck;

import org.graalvm.internal.tck.utils.MetadataGenerationUtils;
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

public abstract class GenerateMetadataTask extends DefaultTask {
    private static final String GRADLEW = "gradlew";

    private String coordinates;
    private String allowedPackages;

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

    @Option(option = "allowedPackages", description = "Comma separated allowed packages (or - for none)")
    public void setAllowedPackages(String allowedPackages) {
        this.allowedPackages = allowedPackages;
    }

    @Input
    @Optional
    public String getAllowedPackages() {
        return allowedPackages;
    }

    @TaskAction
    public void run() throws IOException {
        Path testsDirectory = MetadataGenerationUtils.computeTestsDirectory(getLayout(), coordinates);
        Path gradlewPath = MetadataGenerationUtils.getPathFromProject(getLayout(), GRADLEW);
        
        List<String> packageList = (allowedPackages == null || allowedPackages.isBlank() || allowedPackages.equals("-"))
                ? List.of()
                : Arrays.stream(allowedPackages.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());

        if (!packageList.isEmpty()) {
            MetadataGenerationUtils.addUserCodeFilterFile(testsDirectory, packageList);
        }
        MetadataGenerationUtils.addAgentConfigBlock(testsDirectory);
        MetadataGenerationUtils.collectMetadata(getExecOperations(), testsDirectory, getLayout(), coordinates, gradlewPath);
        Path metadataDirectory = MetadataGenerationUtils.computeMetadataDirectory(getLayout(), coordinates);
        MetadataGenerationUtils.createIndexJsonSpecificVersion(metadataDirectory);
    }
}
