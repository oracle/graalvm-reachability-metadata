package org.graalvm.internal.tck;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

public class DockerUtils {

    public static Set<String> getAllowedImages() throws IOException {
        Set<String> allowedImagesList = new HashSet<>();
        InputStream allowedImages = DockerTask.class.getResourceAsStream("/AllowedDockerImages.txt");
        BufferedReader isr = new BufferedReader(new InputStreamReader(allowedImages));
        String image;
        while ((image = isr.readLine()) != null) {
            allowedImagesList.add(image);
        }

        isr.close();
        allowedImages.close();

        return allowedImagesList;
    }

}
