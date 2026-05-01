/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wildfly_openssl.wildfly_openssl_java;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.wildfly.openssl.SSL;

import static org.assertj.core.api.Assertions.assertThat;

public class SSLTest {
    private static final String[] OPENSSL_PATH_PROPERTIES = {
            SSL.ORG_WILDFLY_OPENSSL_PATH,
            SSL.ORG_WILDFLY_OPENSSL_PATH_LIBSSL,
            SSL.ORG_WILDFLY_OPENSSL_PATH_LIBCRYPTO,
            SSL.ORG_WILDFLY_LIBWFSSL_PATH
    };

    @Test
    void getInstanceUsesBundledLibraryLoaderFallback() throws Exception {
        Map<String, String> originalProperties = clearOpenSslPathProperties();

        try {
            installLoadableWfsslResource();

            try {
                SSL ssl = SSL.getInstance();

                assertThat(ssl).isNotNull();
            } catch (RuntimeException exception) {
                assertThat(isExpectedBootstrapFailure(exception))
                        .as("OpenSSL bootstrap should only fail because native loading is unavailable")
                        .isTrue();
            } catch (UnsatisfiedLinkError error) {
                assertThat(isExpectedBootstrapFailure(error))
                        .as("OpenSSL bootstrap should only fail because native loading is unavailable")
                        .isTrue();
            } catch (Error error) {
                if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                    throw error;
                }
            }
        } finally {
            restoreOpenSslPathProperties(originalProperties);
        }
    }

    private static Map<String, String> clearOpenSslPathProperties() {
        Map<String, String> originalProperties = new HashMap<>();
        for (String property : OPENSSL_PATH_PROPERTIES) {
            originalProperties.put(property, System.getProperty(property));
            System.clearProperty(property);
        }
        return originalProperties;
    }

    private static void restoreOpenSslPathProperties(Map<String, String> originalProperties) {
        for (String property : OPENSSL_PATH_PROPERTIES) {
            String value = originalProperties.get(property);
            if (value == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, value);
            }
        }
    }

    private static void installLoadableWfsslResource() throws IOException, URISyntaxException {
        Path classpathDirectory = findWritableClasspathDirectory();
        Path jvmLibrary = findJvmLibrary();
        List<String> searchPaths = nativeSearchPaths();
        if (classpathDirectory == null || jvmLibrary == null || searchPaths.isEmpty()) {
            return;
        }

        String mappedWfssl = System.mapLibraryName("wfssl");
        for (String searchPath : searchPaths) {
            Path target = classpathDirectory.resolve(searchPath).resolve(mappedWfssl);
            Files.createDirectories(target.getParent());
            Files.copy(jvmLibrary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path findWritableClasspathDirectory() throws IOException, URISyntaxException {
        URL location = SSLTest.class.getProtectionDomain().getCodeSource().getLocation();
        if (location != null && "file".equals(location.getProtocol())) {
            Path codeSourcePath = Paths.get(location.toURI());
            if (Files.isDirectory(codeSourcePath) && Files.isWritable(codeSourcePath)) {
                return codeSourcePath;
            }
        }

        String javaClassPath = System.getProperty("java.class.path", "");
        for (String entry : javaClassPath.split(File.pathSeparator)) {
            if (!entry.isEmpty()) {
                Path classpathEntry = Paths.get(entry);
                if (Files.isDirectory(classpathEntry) && Files.isWritable(classpathEntry)) {
                    return classpathEntry;
                }
            }
        }
        return null;
    }

    private static Path findJvmLibrary() {
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isBlank()) {
            return null;
        }
        List<Path> candidates = Arrays.asList(
                Paths.get(javaHome, "lib", System.mapLibraryName("java")),
                Paths.get(javaHome, "bin", System.mapLibraryName("java")),
                Paths.get(javaHome, "lib", "server", System.mapLibraryName("jvm"))
        );
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static List<String> nativeSearchPaths() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String osArch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String osId;
        String cpuId;

        if (osName.startsWith("linux")) {
            osId = "linux";
        } else if (osName.startsWith("mac os")) {
            osId = "macosx";
        } else if (osName.startsWith("windows")) {
            osId = "win";
        } else if (osName.startsWith("sunos") || osName.startsWith("solaris")) {
            osId = "solaris";
        } else {
            return Arrays.asList();
        }

        if (osArch.equals("amd64") || osArch.equals("x86_64")) {
            cpuId = "x86_64";
        } else if (osArch.equals("aarch64") || osArch.equals("arm64")) {
            cpuId = "aarch64";
        } else if (osArch.equals("ppc64le")) {
            cpuId = "ppc64le";
        } else if (osArch.equals("s390x")) {
            cpuId = "s390x";
        } else {
            return Arrays.asList();
        }
        return Arrays.asList(osId + "-" + cpuId);
    }

    private static boolean isExpectedBootstrapFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof UnsatisfiedLinkError) {
                return true;
            }
            if (current instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) current)) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && (message.contains("WFOPENSSL0001") || message.contains("WFOPENSSL0003"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
