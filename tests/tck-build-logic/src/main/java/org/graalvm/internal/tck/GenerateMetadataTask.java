package org.graalvm.internal.tck;

import org.graalvm.internal.tck.utils.MetadataGenerationUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.tasks.Input;
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
    private String packages;

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

    @Option(option = "packages", description = "Comma separated allowed packages (or - for none)")
    public void setPackages(String packages) {
        this.packages = packages;
    }

    @Input
    public String getPackages() {
        return packages;
    }

    @TaskAction
    public void run() throws IOException {
        Path testsDirectory = MetadataGenerationUtils.computeTestsDirectory(getLayout(), coordinates);
        Path gradlewPath = MetadataGenerationUtils.getPathFromProject(getLayout(), GRADLEW);
        
        List<String> packageList = (packages == null || packages.isBlank() || packages.equals("-"))
                ? List.of()
                : Arrays.stream(packages.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());

        MetadataGenerationUtils.addUserCodeFilterFile(testsDirectory, packageList);
        MetadataGenerationUtils.addAgentConfigBlock(testsDirectory);
        MetadataGenerationUtils.collectMetadata(getExecOperations(), testsDirectory, getLayout(), coordinates, gradlewPath);
    }
}
