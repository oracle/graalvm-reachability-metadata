package org.graalvm.internal.tck;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.*;
import java.util.function.Predicate;

import static org.graalvm.internal.tck.DockerUtils.getAllowedImages;

public class GrypeTask extends DefaultTask {

    private final String jqMatcher = " | jq -c '.matches | .[] | .vulnerability | select(.severity | (contains(\"High\") or contains(\"Critical\")))'";
    @TaskAction
    void run() throws IllegalStateException, IOException, InterruptedException {
        List<String> vulnerableImages = new ArrayList<>();
        Set<String> allowedImages = getAllowedImages();
        for (String image : allowedImages) {
            System.out.println("Checking image: " + image);
            String[] command = { "/bin/sh", "-c",  "grype -o json " + image + jqMatcher };
            Process proc = Runtime.getRuntime().exec(command);

            try (BufferedReader stdOut = proc.inputReader()) {
                if (stdOut.lines().findAny().isPresent()) {
                    vulnerableImages.add(image);
                }
            }
        }

        if (!vulnerableImages.isEmpty()) {
            System.err.println("Vulnerable images found:");
            System.err.println("===========================================================");
            vulnerableImages.forEach(System.err::println);
            throw new IllegalStateException("Vulnerable images found");
        }
    }

}
