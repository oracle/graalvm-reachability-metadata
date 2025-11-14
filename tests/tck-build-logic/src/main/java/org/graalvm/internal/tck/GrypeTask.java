package org.graalvm.internal.tck;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.graalvm.internal.tck.model.grype.GrypeEntry;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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

    private static final String HIGH_VULNERABILITY = "HIGH";
    private static final String CRITICAL_VULNERABILITY = "CRITICAL";

    private record Vulnerabilities(int critical, int high){}

    private record DockerImage(String image, Vulnerabilities vulnerabilities) {
        public String getImageName() {
            return DockerUtils.getImageName(image);
        }

        public boolean isVulnerableImage() {
            return vulnerabilities.critical() > 0 || vulnerabilities.high() > 0;
        }

        public boolean isNoMoreVulnerable(DockerImage other) {
            return this.vulnerabilities.critical() <= other.vulnerabilities().critical() && this.vulnerabilities.high() <= other.vulnerabilities().high();
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
     * If changed images are no more vulnerable than previously allowed images, they won't be reported as vulnerable
     */
    private void scanChangedImages() throws IOException, URISyntaxException {
        Set<DockerImage> imagesToCheck = getChangedImages().stream().map(this::makeDockerImage).collect(Collectors.toSet());
        List<DockerImage> vulnerableImages = imagesToCheck.stream().filter(DockerImage::isVulnerableImage).toList();

        if (!vulnerableImages.isEmpty()) {
            int acceptedImages = 0;
            Set<String> currentlyAllowedImages = getAllowedImagesFromMaster();

            for (DockerImage image : vulnerableImages) {
                image.printVulnerabilityStatus();

                // get allowed image with the same name, if it exists
                Optional<String> existingAllowedImage = currentlyAllowedImages.stream()
                        .filter(allowedImage -> DockerUtils.getImageName(allowedImage).equalsIgnoreCase(image.getImageName()))
                        .findFirst();

                // check if a new image is no more vulnerable than the existing one
                if (existingAllowedImage.isPresent()) {
                    DockerImage imageToCompare = makeDockerImage(existingAllowedImage.get());
                    imageToCompare.printVulnerabilityStatus();

                    if (image.isNoMoreVulnerable(imageToCompare)) {
                        System.out.println("Accepting: " + image.image() + " because it has no more vulnerabilities than existing: " + imageToCompare.image());
                        acceptedImages++;
                    }
                }
            }

            if (acceptedImages < vulnerableImages.size()) {
                throw new IllegalStateException("Highly vulnerable images found. Please check the list of vulnerable images provided above.");
            }
        }
    }

    private DockerImage makeDockerImage(String image) {
        System.out.println("Generating info for docker image: " + image);
        try {
            return new DockerImage(image, getVulnerabilities(image));
        } catch (IOException e) {
            throw new RuntimeException("Cannot parse grype output for image: " + image + " .Reason: " + e.getMessage());
        }
    }

    private Vulnerabilities getVulnerabilities(String image) throws IOException {
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
        ObjectMapper mapper = new ObjectMapper();
        JsonFactory factory = mapper.getFactory();
        try (JsonParser parser = factory.createParser(inputStream)) {
            while (!parser.isClosed()) {
                if (parser.nextToken() == null) {
                    break;
                }

                if (parser.currentToken() != com.fasterxml.jackson.core.JsonToken.START_OBJECT) {
                    continue;
                }

                GrypeEntry vuln = mapper.readValue(parser, GrypeEntry.class);
                if (vuln.severity.equalsIgnoreCase(CRITICAL_VULNERABILITY)) {
                    numberOfCritical++;
                }

                if (vuln.severity.equalsIgnoreCase(HIGH_VULNERABILITY)) {
                    numberOfHigh++;
                }
            }
        }

        return new Vulnerabilities(numberOfCritical, numberOfHigh);
    }

    /**
     * Get all docker images introduced between two commits
     */
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

    /**
     * Return all allowed docker images from master branch
     */
    private Set<String> getAllowedImagesFromMaster() throws URISyntaxException, IOException {
        URL url = GrypeTask.class.getResource(DockerUtils.ALLOWED_DOCKER_IMAGES);
        if (url == null) {
            throw new RuntimeException("Cannot find allowed-docker-images directory");
        }

        Set<String> allowedImages = new HashSet<>();
        try (FileSystem fs = FileSystems.newFileSystem(url.toURI(), Collections.emptyMap())) {
            List<String> files = Files.walk(fs.getPath(DockerUtils.ALLOWED_DOCKER_IMAGES))
                    .filter(Files::isRegularFile)
                    .map(Path::toString)
                    .map(path -> path.substring(path.lastIndexOf("/") + 1))
                    .map(DockerUtils::getDockerFile)
                    .map(DockerUtils::fileNameFromJar)
                    .toList();

            for (String file : files) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                getExecOperations().exec(spec -> {
                    spec.setStandardOutput(baos);
                    spec.commandLine("git", "show", "origin/master:tests/tck-build-logic/src/main/resources" + file);
                });

                allowedImages.add(baos.toString());
            }
        }

        return allowedImages.stream().map(line -> line.substring("FROM".length()).trim()).collect(Collectors.toSet());
    }
}
