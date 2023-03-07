package org.graalvm.internal.tck;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import static org.graalvm.internal.tck.DockerUtils.getAllowedImages;

public class DockerTask extends DefaultTask {

    private String coordinates;

    @Option(option = "coordinates", description = "Coordinates in the form of group:artifact:version")
    void setCoordinates(String coordinates) {
        this.coordinates = coordinates;
    }

    @TaskAction
    void run() throws IOException, IllegalStateException, URISyntaxException {

        Coordinates coordinates = Coordinates.parse(this.coordinates);
        String coordinatesTestRoot = getProject().file(CoordinateUtils.replace("tests/src/$group$/$artifact$/$version$", coordinates)).getPath();
        File dockerList = new File(coordinatesTestRoot + "/required-docker-images.txt");

        if (!dockerList.exists()) {
            System.out.println("Required docker images file don't exist. If your tests use docker, please read: "
                    + new URI("https://github.com/oracle/graalvm-reachability-metadata/blob/master/CONTRIBUTING.md#providing-the-tests-that-use-docker"));
            return;
        }

        Set<String> allowedImages = getAllowedImages();
        BufferedReader isr = new BufferedReader(new InputStreamReader(new FileInputStream(dockerList)));
        String image;
        while ((image = isr.readLine()) != null) {
            if (allowedImages.contains(image)) {
                pullDockerImage(image);
            } else {
                throw new IllegalStateException("Image " + image + " is not listed in our allowed docker images. If you want to use this docker image, " +
                        "please create a separate PR in which you will add this image to the list.");
            }
        }

        isr.close();
    }

    private void pullDockerImage(String image) {
        System.out.println("Pulling image " + image + "...");
        try {
            Runtime.getRuntime().exec("docker pull " + image).waitFor();
        } catch (IOException | InterruptedException e) {
            System.out.println("Docker pull failed: " + e.getMessage());
        }
    }
}
