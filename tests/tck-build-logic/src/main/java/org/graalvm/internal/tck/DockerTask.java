package org.graalvm.internal.tck;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

import static org.graalvm.internal.tck.DockerUtils.getAllowedImages;

public abstract class DockerTask extends DefaultTask {

    @InputFiles
    protected abstract RegularFileProperty getRequiredImagesFile();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Option(option = "coordinates", description = "Coordinates in the form of group:artifact:version")
    void setCoordinates(String coords) {
        Coordinates coordinates = Coordinates.parse(coords);
        String coordinatesTestRoot = getProject().file(CoordinateUtils.replace("tests/src/$group$/$artifact$/$version$", coordinates)).getPath();
        Path requiredDockerImagesFile = Paths.get(coordinatesTestRoot + "/required-docker-images.txt");
        File dockerList = new File(requiredDockerImagesFile.toString());
        getRequiredImagesFile().set(dockerList);
    }

    @TaskAction
    void run() throws IOException, IllegalStateException, URISyntaxException {
        File dockerList = getRequiredImagesFile().get().getAsFile();

        if (!dockerList.exists()) {
            System.out.println("Required docker images file don't exist. If your tests use docker, please read: "
                    + new URI("https://github.com/oracle/graalvm-reachability-metadata/blob/master/CONTRIBUTING.md#providing-the-tests-that-use-docker"));
            return;
        }

        Set<String> allowedImages = getAllowedImages();
        List<String> requiredImages = Files.readAllLines(dockerList.toPath());
        for (String image : requiredImages) {
            if (allowedImages.contains(image)) {
                pullDockerImage(image);
            } else {
                throw new IllegalStateException("Image " + image + " is not listed in allowed docker images list. If you want to use this docker image, " +
                        "please create a separate PR in which you will add this image to the list. Please read: "
                        + new URI("https://github.com/oracle/graalvm-reachability-metadata/blob/master/CONTRIBUTING.md#providing-the-tests-that-use-docker"));
            }
        }
    }

    private void pullDockerImage(String image) {
        System.out.println("Pulling image " + image + "...");
        getExecOperations().exec(execSpec -> {
            execSpec.setExecutable("docker");
            execSpec.args(List.of("pull", image));
        });
    }
}
