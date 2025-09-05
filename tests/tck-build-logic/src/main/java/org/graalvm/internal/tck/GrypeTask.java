package org.graalvm.internal.tck;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


public abstract class GrypeTask extends DefaultTask {

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Option(option = "baseCommit", description = "Last commit from master")
    void setBaseCommit(String baseCommit) {
        this.baseCommit = baseCommit;
    }

    @Option(option = "newCommit", description = "HEAD commit of the pull request")
    void setNewCommit(String newCommit) {
        this.newCommit = newCommit;
    }

    private String newCommit;
    private String baseCommit;

    private static final String JQ_MATCHER = " | jq -c '.matches | .[] | .vulnerability | select(.severity | (contains(\"High\") or contains(\"Critical\")))'";
    private static final String DOCKERFILE_DIRECTORY = "allowed-docker-images";

    private record Vulnerabilities(int critical, int high){}

    private record DockerImage(String image, Vulnerabilities vulnerabilities) {
        public String getImageName() {
            return image.split(":")[0];
        }

        public boolean isVulnerableImage() {
            return vulnerabilities.critical() > 0 || vulnerabilities.high() > 0;
        }

        public boolean isLessVulnerable(DockerImage other) {
            // first check number of critical vulnerabilities
            if (this.vulnerabilities.critical() < other.vulnerabilities().critical()) {
                return true;
            }

            // if number of critical vulnerabilities is the same => check number of high vulnerabilities
            return this.vulnerabilities.critical() == other.vulnerabilities().critical() && this.vulnerabilities.high() < other.vulnerabilities().high();
        }

        public void printVulnerabilityStatus() {
            System.out.println("Image: " + image + " contains " + vulnerabilities.critical() + " critical and " + vulnerabilities.high() + " high vulnerabilities");
        }
    }

    @TaskAction
    void run() throws IllegalStateException, IOException, URISyntaxException {
        boolean scanAllAllowedImages = baseCommit == null && newCommit == null;
        if (scanAllAllowedImages) {
            scanAllImages();
        } else {
            scanChangedImages();
        }
    }

    /**
     * Re-scans all images from allowed images list
     */
    private void scanAllImages() {
        Set<DockerImage> imagesToCheck = DockerUtils.getAllAllowedImages().stream().map(this::makeDockerImage).collect(Collectors.toSet());
        List<DockerImage> vulnerableImages = imagesToCheck.stream().filter(DockerImage::isVulnerableImage).toList();

        if (!vulnerableImages.isEmpty()) {
            vulnerableImages.forEach(DockerImage::printVulnerabilityStatus);
            throw new IllegalStateException("Highly vulnerable images found. Please check the list of vulnerable images provided above.");
        }
    }

    /**
     * Scans images that have been changed between org.graalvm.internal.tck.GrypeTask#baseCommit and org.graalvm.internal.tck.GrypeTask#newCommit.
     *
     * If changed images are less vulnerable than previously allowed images, they won't be reported as vulnerable
     */
    private void scanChangedImages() throws IOException, URISyntaxException {
        Set<DockerImage> currentlyAllowedImages = DockerUtils.getAllAllowedImages().stream().map(this::makeDockerImage).collect(Collectors.toSet());
        Set<DockerImage> imagesToCheck = getChangedImages().stream().map(this::makeDockerImage).collect(Collectors.toSet());
        List<DockerImage> vulnerableImages = imagesToCheck.stream().filter(DockerImage::isVulnerableImage).toList();

        if (!vulnerableImages.isEmpty()) {
            boolean shouldFail = false;
            for (DockerImage image : vulnerableImages) {
                // check if a new image is less vulnerable than the existing allowed one
                Optional<DockerImage> existingAllowedImage = currentlyAllowedImages.stream().filter(i -> i.getImageName().equalsIgnoreCase(image.getImageName())).findFirst();
                if (existingAllowedImage.isPresent() && !image.isLessVulnerable(existingAllowedImage.get())) {
                    image.printVulnerabilityStatus();
                    shouldFail = true;
                }
            }

            if (shouldFail) {
                throw new IllegalStateException("Highly vulnerable images found. Please check the list of vulnerable images provided above.");
            }
        }
    }

    private DockerImage makeDockerImage(String image) {
        return new DockerImage(image, getVulnerabilities(image));
    }

    private Vulnerabilities getVulnerabilities(String image) {
        int numberOfHigh = 0;
        int numberOfCritical = 0;
        String[] command = {"-c", "grype -o json " + image + JQ_MATCHER};

        // call Grype to get vulnerabilities
        ByteArrayOutputStream execOutput = new ByteArrayOutputStream();
        getExecOperations().exec(execSpec -> {
            execSpec.setExecutable("/bin/sh");
            execSpec.setArgs(List.of(command));
            execSpec.setStandardOutput(execOutput);
        });

        // parse Grype output
        ByteArrayInputStream inputStream = new ByteArrayInputStream(execOutput.toByteArray());
        try (BufferedReader stdOut = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = stdOut.readLine()) != null) {
                if (line.contains("\"severity\":\"High\"")) {
                    numberOfHigh++;
                } else if (line.contains("\"severity\":\"Critical\"")) {
                    numberOfCritical++;
                }
            }

            inputStream.close();
            execOutput.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new Vulnerabilities(numberOfCritical, numberOfHigh);
    }


    private Set<String> getChangedImages() throws IOException, URISyntaxException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        getExecOperations().exec(spec -> {
            spec.setStandardOutput(baos);
            spec.commandLine("git", "diff", "--name-only", "--diff-filter=ACMRT", baseCommit, newCommit);
        });

        String output = baos.toString(StandardCharsets.UTF_8);
        List<URL> diffFiles = Arrays.stream(output.split("\\r?\\n"))
                .filter(path -> path.contains(DOCKERFILE_DIRECTORY))
                .map(path -> path.substring(path.lastIndexOf("/") + 1))
                .map(DockerUtils::getDockerFile)
                .toList();

        if (diffFiles.isEmpty()) {
            throw new RuntimeException("There are no changed or new docker image founded. " +
                    "This task should be executed only if there are changes in allowed-docker-images directory.");
        }

        return DockerUtils.extractImagesNames(diffFiles);
    }
}
