package org.graalvm.internal.tck;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.api.GradleException;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * Pulls docker images listed in a text file (one image per line).
 * Fails the task immediately if any image pull fails to ensure batch execution fails on single error.
 */
public abstract class PullImagesFromFileTask extends DefaultTask {

    @InputFile
    public abstract RegularFileProperty getImagesFile();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void run() throws IOException {
        File inFile = getImagesFile().get().getAsFile();
        if (!inFile.exists()) {
            getLogger().lifecycle("No required docker images collected: {}", inFile.getAbsolutePath());
            return;
        }

        List<String> images = Files.readAllLines(inFile.toPath()).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                .toList();

        for (String image : images) {
            getLogger().lifecycle("Pulling image {}...", image);
            try {
                getExecOperations().exec(spec -> {
                    spec.setExecutable("docker");
                    spec.args("pull", image);
                });
            } catch (Exception e) {
                throw new GradleException("Failed to pull image " + image + ": " + e.getMessage(), e);
            }
        }
    }
}
