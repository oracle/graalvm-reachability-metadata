/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aesh.aesh;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import org.aesh.io.scanner.ClassFileIterator;
import org.aesh.io.scanner.ResourceIterator;

@TargetClass(className = "org.aesh.io.scanner.AnnotationDetector")
final class Target_AnnotationDetector {

    @Substitute
    public void detect(String... packages) throws IOException {
        String[] packagePaths = new String[packages.length];
        for (int i = 0; i < packagePaths.length; i++) {
            String packagePath = packages[i].replace('.', '/');
            packagePaths[i] = packagePath.endsWith("/") ? packagePath : packagePath + "/";
        }

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Set<File> scanRoots = new LinkedHashSet<>();
        ArrayList<Path> tempRoots = new ArrayList<>();

        try {
            for (String packagePath : packagePaths) {
                Enumeration<URL> resources = classLoader.getResources(packagePath);
                while (resources.hasMoreElements()) {
                    URL resourceUrl = resources.nextElement();
                    String protocol = resourceUrl.getProtocol();
                    if ("file".equals(protocol)) {
                        scanRoots.add(AnnotationDetectorSupport.toFile(resourceUrl));
                    } else if ("resource".equals(protocol)) {
                        Path tempRoot = AnnotationDetectorSupport.extractResourceDirectory(classLoader, resourceUrl, packagePath);
                        tempRoots.add(tempRoot);
                        scanRoots.add(tempRoot.toFile());
                    } else if ("jar".equals(protocol) || "zip".equals(protocol) || protocol.startsWith("bundle")) {
                        scanRoots.add(AnnotationDetectorSupport.resolveArchiveFile(resourceUrl));
                    } else {
                        throw new AssertionError("Unknown URL protocol: " + protocol);
                    }
                }
            }

            if (!scanRoots.isEmpty()) {
                detect(new ClassFileIterator(scanRoots.toArray(File[]::new), packagePaths));
            }
        } finally {
            for (Path tempRoot : tempRoots) {
                AnnotationDetectorSupport.deleteRecursively(tempRoot);
            }
        }
    }

    @Alias
    private native void detect(ResourceIterator resourceIterator) throws IOException;
}

final class AnnotationDetectorSupport {
    private AnnotationDetectorSupport() {
    }

    static File resolveArchiveFile(URL resourceUrl) throws IOException {
        URL archiveUrl = resourceUrl;
        if ("zip".equals(resourceUrl.getProtocol())) {
            archiveUrl = new URL(resourceUrl.toExternalForm().replace("zip:/", "jar:file:/"));
        }
        URLConnection connection = archiveUrl.openConnection();
        if (archiveUrl.getProtocol().startsWith("bundle")) {
            connection = readBundleLocalUrl(connection).openConnection();
        }
        if (connection instanceof JarURLConnection jarURLConnection) {
            return toFile(jarURLConnection.getJarFileURL());
        }
        throw new AssertionError("Unknown URLConnection type: " + connection.getClass().getName());
    }

    private static URL readBundleLocalUrl(URLConnection connection) {
        try {
            return (URL) connection.getClass().getDeclaredMethod("getLocalURL").invoke(connection);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Couldn't read jar file URL from bundle: " + exception);
        }
    }

    static Path extractResourceDirectory(ClassLoader classLoader, URL directoryUrl, String packagePath) throws IOException {
        Path tempRoot = Files.createTempDirectory("aesh-scan-");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(directoryUrl.openStream(), StandardCharsets.UTF_8))) {
            String resourceName;
            while ((resourceName = reader.readLine()) != null) {
                if (!resourceName.endsWith(".class")) {
                    continue;
                }
                URL classUrl = classLoader.getResource(packagePath + resourceName);
                if (classUrl == null) {
                    continue;
                }
                Path targetPath = tempRoot.resolve(packagePath).resolve(resourceName);
                Files.createDirectories(targetPath.getParent());
                try (InputStream inputStream = classUrl.openStream()) {
                    Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        return tempRoot;
    }

    static File toFile(URL url) {
        return Path.of(toUri(url)).toFile();
    }

    private static URI toUri(URL url) {
        try {
            return url.toURI();
        } catch (Exception exception) {
            throw new AssertionError("Unable to convert URI to File: " + url);
        }
    }

    static void deleteRecursively(Path root) {
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
