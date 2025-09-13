package org.graalvm.internal.tck.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

public class FilesUtils {

    public static void findJavaFiles(Path root, List<Path> result) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (Files.isRegularFile(file) && file.toString().endsWith(".java")) {
                    result.add(file);
                }

                return super.visitFile(file, attrs);
            }
        });
    }
}
