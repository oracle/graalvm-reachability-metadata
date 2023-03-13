package org.graalvm.internal.tck;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class DockerUtils {

    public static Set<String> getAllowedImages() throws IOException {
        return new HashSet<>(Files.readAllLines(Paths.get("./tests/tck-build-logic/src/main/resources/AllowedDockerImages.txt")));
    }

}
