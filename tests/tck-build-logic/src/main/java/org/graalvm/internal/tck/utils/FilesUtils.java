package org.graalvm.internal.tck.utils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class FilesUtils {

    public static void findJavaFiles(Path root, List<Path> result) {
        if (Files.exists(root) && Files.isRegularFile(root) && root.toString().endsWith(".java")) {
            result.add(root);
            return;
        }

        if (Files.isDirectory(root)) {
            File[] content = root.toFile().listFiles();
            if (content == null) {
                return;
            }

            for (var file : content) {
                findJavaFiles(file.toPath(), result);
            }
        }
    }
}
