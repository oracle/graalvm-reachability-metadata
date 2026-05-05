/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.graalvm.internal.tck.NativeImageSupport;
import org.h2.util.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

public class UtilsTest {
    @Test
    void loadsResourceFromBundledDataZip() throws Exception {
        String missingResource = "/org/h2/server/web/res/missing-" + System.nanoTime() + ".txt";

        assertThat(Utils.getResource(missingResource)).isNull();
        byte[] resource = Utils.getResource("/org/h2/server/web/res/_text_en.prop");

        assertThat(resource).isNotNull();
        assertThat(new String(resource, StandardCharsets.UTF_8)).contains("H2 Console");
    }

    @Test
    void invokesStaticMethodByName() throws Exception {
        Object result = Utils.callStaticMethod(StaticMethodTarget.class.getName() + ".join", "prefix", 7);

        assertThat(result).isEqualTo("prefix-7");
    }

    @Test
    void invokesInstanceMethodByName() throws Exception {
        InstanceMethodTarget target = new InstanceMethodTarget("left");

        Object result = Utils.callMethod(target, "combine", "right");

        assertThat(result).isEqualTo("left:right");
    }

    @Test
    void createsInstanceByClassName() throws Exception {
        Object result = Utils.newInstance(ConstructorTarget.class.getName(), "created", 3);

        assertThat(result).isInstanceOf(ConstructorTarget.class);
        assertThat(result.toString()).isEqualTo("created#3");
    }

    @Test
    void scalesValueForAvailableMemory() {
        int scaled = Utils.scaleForAvailableMemory(16);

        assertThat(scaled).isGreaterThanOrEqualTo(0);
    }

    @Test
    void loadsResourceDirectlyWhenBundledDataZipIsUnavailable(@TempDir Path temporaryDirectory) throws Exception {
        assumeFalse(isNativeImageRuntime(), "Child-first URLClassLoader resource isolation is JVM-only");
        try {
            try (ChildFirstH2ClassLoader classLoader = newIsolatedH2ClassLoader(temporaryDirectory, true, false)) {
                Class<?> isolatedUtils = classLoader.loadClass(Utils.class.getName());

                byte[] resource = invokeGetResource(isolatedUtils, "/h2/fallback-resource.txt");

                assertThat(resource).isNotNull();
                assertThat(new String(resource, StandardCharsets.UTF_8)).isEqualTo("fallback resource");
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void invokesOperatingSystemBeanWhenMaximumHeapIsUnbounded(@TempDir Path temporaryDirectory) throws Exception {
        assumeFalse(isNativeImageRuntime(), "Child-first URLClassLoader resource isolation is JVM-only");
        try {
            try (ChildFirstH2ClassLoader classLoader = newIsolatedH2ClassLoader(temporaryDirectory, false, true)) {
                Class<?> isolatedUtils = classLoader.loadClass(Utils.class.getName());

                Method method = isolatedUtils.getMethod("scaleForAvailableMemory", int.class);
                int scaled = (Integer) method.invoke(null, 16);

                assertThat(scaled).isGreaterThanOrEqualTo(0);
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static byte[] invokeGetResource(Class<?> isolatedUtils, String name) throws Exception {
        Method method = isolatedUtils.getMethod("getResource", String.class);
        return (byte[]) method.invoke(null, name);
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    private static ChildFirstH2ClassLoader newIsolatedH2ClassLoader(
            Path temporaryDirectory, boolean removeDataZip, boolean forceUnboundedMemory) throws Exception {
        Path jar = createIsolatedH2Jar(temporaryDirectory, removeDataZip, forceUnboundedMemory);
        URL[] urls = {jar.toUri().toURL()};
        return new ChildFirstH2ClassLoader(urls, UtilsTest.class.getClassLoader());
    }

    private static Path createIsolatedH2Jar(
            Path temporaryDirectory, boolean removeDataZip, boolean forceUnboundedMemory) throws Exception {
        CodeSource codeSource = Utils.class.getProtectionDomain().getCodeSource();
        Path source = Path.of(codeSource.getLocation().toURI());
        Path jar = temporaryDirectory.resolve("isolated-h2.jar");
        Set<String> entries = new HashSet<>();
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(jar))) {
            if (Files.isDirectory(source)) {
                copyDirectoryToJar(source, output, entries, removeDataZip, forceUnboundedMemory);
            } else {
                copyZipToJar(source, output, entries, removeDataZip, forceUnboundedMemory);
            }
            addEntry(output, entries, "h2/fallback-resource.txt", "fallback resource".getBytes(StandardCharsets.UTF_8));
        }
        return jar;
    }

    private static void copyDirectoryToJar(
            Path source, ZipOutputStream output, Set<String> entries, boolean removeDataZip,
            boolean forceUnboundedMemory) throws IOException {
        try {
            Files.walk(source).filter(Files::isRegularFile).forEach(path -> {
                String name = source.relativize(path).toString().replace('\\', '/');
                try {
                    addFilteredEntry(
                            output, entries, name, Files.readAllBytes(path), removeDataZip, forceUnboundedMemory);
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }
            });
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    private static void copyZipToJar(
            Path source, ZipOutputStream output, Set<String> entries, boolean removeDataZip,
            boolean forceUnboundedMemory) throws IOException {
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(source))) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    addFilteredEntry(
                            output, entries, entry.getName(), readAllBytes(input), removeDataZip, forceUnboundedMemory);
                }
                input.closeEntry();
            }
        }
    }

    private static void addFilteredEntry(
            ZipOutputStream output, Set<String> entries, String name, byte[] bytes, boolean removeDataZip,
            boolean forceUnboundedMemory) throws IOException {
        if (removeDataZip && "org/h2/util/data.zip".equals(name)) {
            return;
        }
        byte[] entryBytes = bytes;
        if (forceUnboundedMemory && "org/h2/util/Utils.class".equals(name)) {
            entryBytes = patchMaxMemory(bytes);
        }
        addEntry(output, entries, name, entryBytes);
    }

    private static void addEntry(ZipOutputStream output, Set<String> entries, String name, byte[] bytes)
            throws IOException {
        if (!entries.add(name)) {
            return;
        }
        output.putNextEntry(new ZipEntry(name));
        output.write(bytes);
        output.closeEntry();
    }

    private static byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        input.transferTo(output);
        return output.toByteArray();
    }

    private static byte[] patchMaxMemory(byte[] originalBytes) {
        byte[] bytes = originalBytes.clone();
        ConstantPoolIndexes indexes = readConstantPoolIndexes(bytes);
        byte[] pattern = {
                (byte) 0xb8, high(indexes.runtimeGetRuntime), low(indexes.runtimeGetRuntime),
                (byte) 0xb6, high(indexes.runtimeMaxMemory), low(indexes.runtimeMaxMemory)
        };
        for (int i = 0; i <= bytes.length - pattern.length; i++) {
            if (matches(bytes, pattern, i)) {
                bytes[i] = 0x14;
                bytes[i + 1] = high(indexes.longMaxValue);
                bytes[i + 2] = low(indexes.longMaxValue);
                bytes[i + 3] = 0;
                bytes[i + 4] = 0;
                bytes[i + 5] = 0;
                return bytes;
            }
        }
        throw new IllegalStateException("Runtime.maxMemory call was not found in Utils.class");
    }

    private static boolean matches(byte[] bytes, byte[] pattern, int offset) {
        for (int i = 0; i < pattern.length; i++) {
            if (bytes[offset + i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }

    private static ConstantPoolIndexes readConstantPoolIndexes(byte[] bytes) {
        int count = readU2(bytes, 8);
        String[] utf = new String[count];
        int[] classNameIndex = new int[count];
        int[] nameAndTypeNameIndex = new int[count];
        int[] nameAndTypeDescriptorIndex = new int[count];
        int[] methodClassIndex = new int[count];
        int[] methodNameAndTypeIndex = new int[count];
        int longMaxValue = 0;
        int offset = 10;
        for (int i = 1; i < count; i++) {
            int tag = bytes[offset] & 0xff;
            offset++;
            switch (tag) {
            case 1:
                int length = readU2(bytes, offset);
                offset += 2;
                utf[i] = new String(bytes, offset, length, StandardCharsets.UTF_8);
                offset += length;
                break;
            case 7:
                classNameIndex[i] = readU2(bytes, offset);
                offset += 2;
                break;
            case 10:
                methodClassIndex[i] = readU2(bytes, offset);
                methodNameAndTypeIndex[i] = readU2(bytes, offset + 2);
                offset += 4;
                break;
            case 12:
                nameAndTypeNameIndex[i] = readU2(bytes, offset);
                nameAndTypeDescriptorIndex[i] = readU2(bytes, offset + 2);
                offset += 4;
                break;
            case 5:
                if (readLong(bytes, offset) == Long.MAX_VALUE) {
                    longMaxValue = i;
                }
                offset += 8;
                i++;
                break;
            default:
                offset = skipConstantPoolEntry(bytes, offset, tag, i);
                if (tag == 6) {
                    i++;
                }
            }
        }
        return findRuntimeIndexes(
                utf, classNameIndex, nameAndTypeNameIndex, nameAndTypeDescriptorIndex, methodClassIndex,
                methodNameAndTypeIndex, longMaxValue);
    }

    private static int skipConstantPoolEntry(byte[] bytes, int offset, int tag, int index) {
        switch (tag) {
        case 3:
        case 4:
        case 9:
        case 11:
        case 12:
        case 17:
        case 18:
            return offset + 4;
        case 6:
            return offset + 8;
        case 8:
        case 16:
        case 19:
        case 20:
            return offset + 2;
        case 15:
            return offset + 3;
        default:
            throw new IllegalStateException("Unsupported constant pool tag " + tag + " at index " + index);
        }
    }

    private static ConstantPoolIndexes findRuntimeIndexes(
            String[] utf, int[] classNameIndex, int[] nameAndTypeNameIndex, int[] nameAndTypeDescriptorIndex,
            int[] methodClassIndex, int[] methodNameAndTypeIndex, int longMaxValue) {
        int runtimeGetRuntime = 0;
        int runtimeMaxMemory = 0;
        for (int i = 1; i < methodClassIndex.length; i++) {
            String className = utf[classNameIndex[methodClassIndex[i]]];
            int nameAndType = methodNameAndTypeIndex[i];
            String methodName = utf[nameAndTypeNameIndex[nameAndType]];
            String descriptor = utf[nameAndTypeDescriptorIndex[nameAndType]];
            if ("java/lang/Runtime".equals(className) && "getRuntime".equals(methodName)
                    && "()Ljava/lang/Runtime;".equals(descriptor)) {
                runtimeGetRuntime = i;
            } else if ("java/lang/Runtime".equals(className) && "maxMemory".equals(methodName)
                    && "()J".equals(descriptor)) {
                runtimeMaxMemory = i;
            }
        }
        if (runtimeGetRuntime == 0 || runtimeMaxMemory == 0 || longMaxValue == 0) {
            throw new IllegalStateException("Required constant pool entries were not found in Utils.class");
        }
        return new ConstantPoolIndexes(runtimeGetRuntime, runtimeMaxMemory, longMaxValue);
    }

    private static int readU2(byte[] bytes, int offset) {
        return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
    }

    private static long readLong(byte[] bytes, int offset) {
        return ((long) (bytes[offset] & 0xff) << 56) | ((long) (bytes[offset + 1] & 0xff) << 48)
                | ((long) (bytes[offset + 2] & 0xff) << 40) | ((long) (bytes[offset + 3] & 0xff) << 32)
                | ((long) (bytes[offset + 4] & 0xff) << 24) | ((long) (bytes[offset + 5] & 0xff) << 16)
                | ((long) (bytes[offset + 6] & 0xff) << 8) | (bytes[offset + 7] & 0xffL);
    }

    private static byte high(int value) {
        return (byte) (value >>> 8);
    }

    private static byte low(int value) {
        return (byte) value;
    }

    private record ConstantPoolIndexes(int runtimeGetRuntime, int runtimeMaxMemory, int longMaxValue) {
    }

    private static final class ChildFirstH2ClassLoader extends URLClassLoader {
        ChildFirstH2ClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        public URL getResource(String name) {
            if ("org/h2/util/data.zip".equals(name) || "h2/fallback-resource.txt".equals(name)) {
                return findResource(name);
            }
            return super.getResource(name);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("org.h2.")) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        try {
                            loaded = findClass(name);
                        } catch (ClassNotFoundException exception) {
                            loaded = super.loadClass(name, resolve);
                        }
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
            return super.loadClass(name, resolve);
        }
    }

    public static class StaticMethodTarget {
        public static String join(String text, int value) {
            return text + '-' + value;
        }
    }

    public static class InstanceMethodTarget {
        private final String left;

        public InstanceMethodTarget(String left) {
            this.left = left;
        }

        public String combine(String right) {
            return left + ':' + right;
        }
    }

    public static class ConstructorTarget {
        private final String text;
        private final int value;

        public ConstructorTarget(String text, int value) {
            this.text = text;
            this.value = value;
        }

        @Override
        public String toString() {
            return text + '#' + value;
        }
    }
}
