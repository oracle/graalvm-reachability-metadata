package org.graalvm.internal.tck;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

public class DockerUtils {
    public static final String ALLOWED_DOCKER_IMAGES = "/allowed-docker-images";

    private static URL getDockerfileDirectory() {
        URL url = DockerUtils.class.getResource(ALLOWED_DOCKER_IMAGES);
        if (url == null) {
            throw new RuntimeException("Cannot find allowed-docker-images directory");
        }

        return url;
    }

    public static URL getDockerFile(String name) {
        try {
            return new URL(getDockerfileDirectory().toString().concat("/" + name));
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private static String imageNameFromFile(URL dockerFile) throws IOException, URISyntaxException {
        final String FROM = "FROM";
        List<String> images = new BufferedReader(new InputStreamReader(dockerFile.openStream()))
                .lines()
                .filter(line -> line.startsWith(FROM))
                .map(line -> line.substring(FROM.length()).trim())
                .toList();
        if (images.size() != 1) {
            throw new RuntimeException("Dockerfile: " + fileNameFromJar(dockerFile) + " must contain only one FROM line, got '" + images.size() + "' (" + images + "). Please read our documentation: "
                    + new URI("https://github.com/oracle/graalvm-reachability-metadata/blob/master/CONTRIBUTING.md#providing-the-tests-that-use-docker"));
        }

        return images.get(0);
    }

    public static String fileNameFromJar(URL jarFile) {
        return jarFile.toString().split("!")[1];
    }

    public static Set<String> extractImagesNames(List<URL> dockerFileNames) throws IOException, URISyntaxException {
        Set<String> allowedImages = new HashSet<>();
        for (URL dockerFile : dockerFileNames) {
            allowedImages.add(imageNameFromFile(dockerFile));
        }

        return allowedImages;
    }

    public static Set<String> getAllAllowedImages() {
        URL url = getDockerfileDirectory();
        try (FileSystem fs = FileSystems.newFileSystem(url.toURI(), Collections.emptyMap())) {
            List<URL> result = Files.walk(fs.getPath(ALLOWED_DOCKER_IMAGES))
                    .filter(Files::isRegularFile)
                    .map(Path::toString)
                    .map(path -> path.substring(path.lastIndexOf("/") + 1))
                    .map(DockerUtils::getDockerFile)
                    .toList();
            
            return extractImagesNames(result);
        } catch (IOException e) {
            throw new RuntimeException("Cannot find files in allowed-docker-images directory");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getImageName(String imageWithVersion) {
        return imageWithVersion.split(":")[0];
    }

}
