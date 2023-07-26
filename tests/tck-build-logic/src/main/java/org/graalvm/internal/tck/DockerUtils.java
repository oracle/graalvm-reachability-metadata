package org.graalvm.internal.tck;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DockerUtils {

    public static Set<String> getAllowedImages() throws IOException, URISyntaxException {
        String dockerfileDirectory = Paths.get("./tests/tck-build-logic/src/main/resources/allowed-docker-images").toString();
        File[] dockerFiles = new File(dockerfileDirectory).listFiles();
        if (dockerFiles == null) {
            throw new RuntimeException("Cannot find allowed-docker-images directory content");
        }

        final String FROM = "FROM";
        Set<String> allowedImages = new HashSet<>();
        for (File dockerFile : dockerFiles) {
            List<String> images = Files.readAllLines(dockerFile.toPath())
                    .stream()
                    .filter(line -> line.startsWith(FROM))
                    .map(line -> line.substring(FROM.length()).trim())
                    .toList();
            if (images.size() != 1) {
                throw new RuntimeException("Dockerfile: " + dockerFile.getName() + " must contain only one FROM line, got '" + images.size() + "' (" + images + "). Please read our documentation: "
                + new URI("https://github.com/oracle/graalvm-reachability-metadata/blob/master/CONTRIBUTING.md#providing-the-tests-that-use-docker"));
            }

            allowedImages.add(images.get(0));
        }

        return allowedImages;
    }

}
