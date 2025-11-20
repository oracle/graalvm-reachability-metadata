package org.graalvm.internal.tck.harness.tasks;

import org.graalvm.internal.tck.DockerUtils;
import org.graalvm.internal.tck.harness.TckExtension;
import org.graalvm.internal.tck.utils.CoordinateUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.*;

/**
 * Single task that:
 * 1) Computes the union of required docker images across selected coordinates (supports fractional batching k/n)
 * 2) Validates them against the allowed-docker-images list
 * 3) Writes them to the provided output file
 * 4) Immediately pulls those images
 * </p>
 * Coordinates can be provided via:
 * - -Pcoordinates=<filter> (preferred)
 * - --coordinates=<filter>
 * The filter can be <code>group:artifact:version</code> or a fractional batch in the form <code>k/n</code> (e.g., 1/16), or 'all'.
 */
public abstract class ComputeAndPullAllowedDockerImagesTask extends DefaultTask {

    @Input
    @Optional
    public abstract Property<@NotNull String> getCoordinates();

    @Option(option = "coordinates", description = "Coordinate filter (group[:artifact[:version]] or k/n fractional batch)")
    public void setCoordinatesOption(String value) {
        getCoordinates().set(value);
    }

    @Inject
    protected abstract ExecOperations getExecOperations();

    protected String effectiveCoordinateFilter() {
        // Prefer task option, fallback to -Pcoordinates, then empty string (all)
        String opt = getCoordinates().getOrNull();
        if (opt != null) {
            return opt;
        }
        Object prop = getProject().findProperty("coordinates");
        return prop == null ? "" : prop.toString();
    }

    @TaskAction
    public void run() throws IOException {
        TckExtension tck = Objects.requireNonNull(getProject().getExtensions().findByType(TckExtension.class));

        // Resolve coordinates
        String filter = effectiveCoordinateFilter();

        List<String> matching;
        if (CoordinateUtils.isFractionalBatch(filter)) {
            int[] frac = CoordinateUtils.parseFraction(filter);
            assert frac != null : "Already checked";
            List<String> all = tck.getMatchingCoordinates("all");
            matching = CoordinateUtils.computeBatchedCoordinates(all, frac[0], frac[1]);
        } else {
            matching = tck.getMatchingCoordinates(filter);
        }

        if (matching == null || matching.isEmpty()) {
            throw new GradleException("No matching coordinates found. Provide --coordinates=<filter> (preferred) or -Pcoordinates=<filter>, or a fractional batch 'k/n'.");
        }

        // Collect union of required docker images
        Set<String> requiredImages = new LinkedHashSet<>();
        for (String c : matching) {
            String[] parts = c.split(":");
            if (parts.length < 3) {
                throw new GradleException("Invalid coordinates: " + c);
            }
            String group = parts[0];
            String artifact = parts[1];
            String version = parts[2];
            File f = getProject().file("tests/src/" + group + "/" + artifact + "/" + version + "/required-docker-images.txt");
            if (f.exists()) {
                Files.readAllLines(f.toPath()).stream()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .filter(s -> !s.startsWith("#"))
                        .forEach(requiredImages::add);
            }
        }

        // Validate against allowed images
        validateRequiredImages(requiredImages);

        // Pull images immediately
        for (String image : requiredImages) {
            getLogger().lifecycle("Pulling docker image {}", image);
            try {
                getExecOperations().exec(spec -> {
                    spec.setExecutable("docker");
                    spec.args("pull", image);
                });
            } catch (Exception e) {
                throw new GradleException("Failed to pull image " + image + ": " + e.getMessage(), e);
            }
        }

        if (requiredImages.isEmpty()) {
            getLogger().lifecycle("No required docker images found for coordinates filter '{}'. If your tests use docker, please read: {}", filter,
                    URI.create("https://github.com/oracle/graalvm-reachability-metadata/blob/master/CONTRIBUTING.md#providing-the-tests-that-use-docker"));
        }
    }

    private static void validateRequiredImages(Set<String> requiredImages) {
        Set<String> allowed = DockerUtils.getAllAllowedImages();
        List<String> notAllowed = new ArrayList<>();
        for (String img : requiredImages) {
            if (!allowed.contains(img)) {
                notAllowed.add(img);
            }
        }
        if (!notAllowed.isEmpty()) {
            throw new GradleException("""
                    The following images are not in the allowed list: %s. \
                    If you need them, add Dockerfiles under tests/tck-build-logic/src/main/resources/allowed-docker-images \
                    per docs/CONTRIBUTING.md, or adjust required-docker-images.txt files.
                    """.formatted(notAllowed));
        }
    }
}
