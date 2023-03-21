package org.graalvm.internal.tck;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.impldep.com.esotericsoftware.kryo.io.ByteBufferOutputStream;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import javax.inject.Inject;
import java.io.*;
import java.util.*;
import java.util.function.Predicate;

import static org.graalvm.internal.tck.DockerUtils.getAllowedImages;

public abstract class GrypeTask extends DefaultTask {

    @Inject
    protected abstract ExecOperations getExecOperations();

    private final String jqMatcher = " | jq -c '.matches | .[] | .vulnerability | select(.severity | (contains(\"High\") or contains(\"Critical\")))'";

    @TaskAction
    void run() throws IllegalStateException, IOException {
        List<String> vulnerableImages = new ArrayList<>();
        Set<String> allowedImages = getAllowedImages();
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
