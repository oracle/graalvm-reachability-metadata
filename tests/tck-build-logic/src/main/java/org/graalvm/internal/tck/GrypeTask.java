package org.graalvm.internal.tck;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;

import static org.graalvm.internal.tck.DockerUtils.extractImagesNames;
import static org.graalvm.internal.tck.DockerUtils.getAllAllowedImages;

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

    private final String jqMatcher = " | jq -c '.matches | .[] | .vulnerability | select(.severity | (contains(\"High\") or contains(\"Critical\")))'";

    private List<String> getChangedImages(String base, String head){
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        getExecOperations().exec(spec -> {
            spec.setStandardOutput(baos);
            spec.commandLine("git", "diff", "--name-only", "--diff-filter=ACMRT", base, head);
        });

        String output = baos.toString(StandardCharsets.UTF_8);
        List<String> diffFiles = Arrays.asList(output.split("\\r?\\n"));
        String dockerfileDirectory = Paths.get("tests/tck-build-logic/src/main/resources/allowed-docker-images").toString();
        diffFiles = diffFiles
                .stream()
                .filter(path -> path.startsWith(dockerfileDirectory))
                .map(path -> path.substring(path.lastIndexOf("/")))
                .toList();

        if (diffFiles.isEmpty()) {
            throw new RuntimeException("There are no changed or new docker image founded. " +
                    "This task should be executed only if there are changes in allowed-docker-images directory.");
        }

        return diffFiles;
    }

    @TaskAction
    void run() throws IllegalStateException, IOException, URISyntaxException {
        List<String> vulnerableImages = new ArrayList<>();
        Set<String> allowedImages;
        if (baseCommit == null && newCommit == null) {
            allowedImages = getAllAllowedImages();
        } else {
            allowedImages = extractImagesNames(getChangedImages(baseCommit, newCommit));
        }

        boolean shouldFail = false;
        for (String image : allowedImages) {
            System.out.println("Checking image: " + image);
            String[] command = { "-c",  "grype -o json " + image + jqMatcher };

            ByteArrayOutputStream execOutput = new ByteArrayOutputStream();
            getExecOperations().exec(execSpec -> {
                execSpec.setExecutable("/bin/sh");
                execSpec.setArgs(List.of(command));
                execSpec.setStandardOutput(execOutput);
            });

            ByteArrayInputStream inputStream = new ByteArrayInputStream(execOutput.toByteArray());
            try (BufferedReader stdOut = new BufferedReader(new InputStreamReader(inputStream))) {
                int numberOfHigh = 0;
                int numberOfCritical = 0;
                String line;
                while ((line = stdOut.readLine()) != null) {
                    if (line.contains("\"severity\":\"High\"")) {
                        numberOfHigh++;
                    }else if (line.contains("\"severity\":\"Critical\"")) {
                        numberOfCritical++;
                    }
                }

                if (numberOfHigh > 0 || numberOfCritical > 0) {
                    vulnerableImages.add("Image: " + image + " contains " + numberOfCritical + " critical, and " + numberOfHigh + " high vulnerabilities");
                }

                if (numberOfHigh > 4 || numberOfCritical > 0) {
                    shouldFail = true;
                }
            }

            inputStream.close();
            execOutput.close();
        }

        if (!vulnerableImages.isEmpty()) {
            System.err.println("Vulnerable images found:");
            System.err.println("===========================================================");
            vulnerableImages.forEach(System.err::println);
        }

        if (shouldFail) {
            throw new IllegalStateException("Highly vulnerable images found. Please check the list of vulnerable images provided above.");
        }
    }

}
