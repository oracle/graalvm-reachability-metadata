/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.graalvm.internal.tck.NativeImageSupport;
import org.h2.util.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

public class UtilsTest {
    private static final String DATA_ZIP_ENTRY = "org/h2/util/data.zip";
    private static final String FALLBACK_RESOURCE = "/h2/utils-fallback-resource.txt";
    private static final String FALLBACK_RESOURCE_CONTENT = "fallback-resource\n";
    private static final String UTILS_CLASS_ENTRY = "org/h2/util/Utils.class";
    private static final byte[] SCALE_FOR_AVAILABLE_MEMORY_BRANCH = {
            (byte) 0xb8, 0x00, 0x0f,
            (byte) 0xb6, 0x00, 0x12,
            0x40,
            0x1f,
            0x14, 0x00, 0x7a,
            (byte) 0x94,
            (byte) 0x99, 0x00, 0x0d
    };

    @TempDir
    Path temporaryDirectory;

    @Test
    void invokesStaticAndInstanceMethodsThroughUtilsReflectionHelpers() throws Exception {
        Object staticResult = Utils.callStaticMethod(UtilsTest.class.getName() + ".staticMessage", "H2");
        assertThat(staticResult).isEqualTo("static:H2");

        Object instance = Utils.newInstance(UtilsTest.class.getName());
        assertThat(instance).isInstanceOf(UtilsTest.class);

        Object instanceResult = Utils.callMethod(instance, "instanceMessage", "covered", 2);
        assertThat(instanceResult).isEqualTo("covered-covered");
    }

    @Test
    void loadsBundledWebConsoleResourceThroughUtilsResourceLoader() throws Exception {
        byte[] resource = Utils.getResource("/org/h2/server/web/res/_text_en.prop");

        assertThat(resource).isNotNull();
        assertThat(resource.length).isGreaterThan(100);
    }

    @Test
    void isolatedUtilsCoversClasspathResourceFallbackAndPhysicalMemoryScaling() throws Exception {
        try {
            Path isolatedJar = createIsolatedH2Jar();
            try (URLClassLoader classLoader = new URLClassLoader(
                    new URL[] {isolatedJar.toUri().toURL()},
                    ClassLoader.getPlatformClassLoader())) {
                Class<?> isolatedUtils = Class.forName(Utils.class.getName(), true, classLoader);

                byte[] resource = (byte[]) invokeStatic(isolatedUtils, "getResource", String.class, FALLBACK_RESOURCE);
                assertThat(new String(resource, StandardCharsets.UTF_8)).isEqualTo(FALLBACK_RESOURCE_CONTENT);

                Integer scaledValue = (Integer) invokeStatic(isolatedUtils, "scaleForAvailableMemory", int.class, 1);
                assertThat(scaledValue).isGreaterThan(0);
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } catch (InvocationTargetException exception) {
            Throwable target = exception.getTargetException();
            if (target instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return;
            }
            throw exception;
        }
    }

    @Test
    void scalesValueForAvailableMemory() {
        assertThat(Utils.scaleForAvailableMemory(1)).isGreaterThanOrEqualTo(0);
    }

    public static String staticMessage(String value) {
        return "static:" + value;
    }

    public String instanceMessage(String value, int repetitions) {
        return String.join("-", Collections.nCopies(repetitions, value));
    }

    private Path createIsolatedH2Jar() throws Exception {
        Path sourceJar = Path.of(Utils.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path isolatedJar = temporaryDirectory.resolve("h2-utils-isolated.jar");

        try (JarFile source = new JarFile(sourceJar.toFile());
                JarOutputStream target = new JarOutputStream(Files.newOutputStream(isolatedJar))) {
            Enumeration<JarEntry> entries = source.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (DATA_ZIP_ENTRY.equals(entry.getName())) {
                    continue;
                }
                byte[] contents = null;
                if (!entry.isDirectory()) {
                    try (InputStream input = source.getInputStream(entry)) {
                        contents = input.readAllBytes();
                    }
                    if (UTILS_CLASS_ENTRY.equals(entry.getName())) {
                        contents = patchScaleForAvailableMemoryBranch(contents);
                    }
                }
                writeJarEntry(target, entry.getName(), contents);
            }
            byte[] fallbackContents = FALLBACK_RESOURCE_CONTENT.getBytes(StandardCharsets.UTF_8);
            writeJarEntry(target, FALLBACK_RESOURCE.substring(1), fallbackContents);
        }
        return isolatedJar;
    }

    private static Object invokeStatic(Class<?> targetClass, String methodName, Class<?> parameterType, Object argument)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Method method = targetClass.getMethod(methodName, parameterType);
        return method.invoke(null, argument);
    }

    private static void writeJarEntry(JarOutputStream target, String name, byte[] contents) throws IOException {
        JarEntry newEntry = new JarEntry(name);
        target.putNextEntry(newEntry);
        if (contents != null) {
            target.write(contents);
        }
        target.closeEntry();
    }

    private static byte[] patchScaleForAvailableMemoryBranch(byte[] contents) {
        int offset = findBranchOffset(contents);
        byte[] patched = contents.clone();
        patched[offset] = (byte) 0x9a;
        return patched;
    }

    private static int findBranchOffset(byte[] contents) {
        for (int i = 0; i <= contents.length - SCALE_FOR_AVAILABLE_MEMORY_BRANCH.length; i++) {
            boolean matches = true;
            for (int j = 0; j < SCALE_FOR_AVAILABLE_MEMORY_BRANCH.length; j++) {
                if (contents[i + j] != SCALE_FOR_AVAILABLE_MEMORY_BRANCH[j]) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return i + SCALE_FOR_AVAILABLE_MEMORY_BRANCH.length - 3;
            }
        }
        throw new IllegalStateException("Could not find Utils.scaleForAvailableMemory branch to patch");
    }
}
