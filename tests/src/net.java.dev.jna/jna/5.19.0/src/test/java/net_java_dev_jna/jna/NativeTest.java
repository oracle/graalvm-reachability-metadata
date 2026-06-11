/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.FromNativeConverter;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Structure;
import com.sun.jna.ToNativeConverter;
import com.sun.jna.TypeMapper;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

public class NativeTest {
    private static final String DARWIN_RESOURCE_PREFIX = "darwin-x86-64";

    static {
        configureDarwinResourcePrefix();
    }

    @Test
    void invokesFunctionThroughLibraryProxy() {
        int number = CLibrary.INSTANCE.atol("42");

        assertThat(number).isEqualTo(42);
    }

    @Test
    @SuppressWarnings("deprecation")
    void loadsDeprecatedLibraryProxyAndSynchronizedProxy() {
        DeprecatedCLibrary library = Native.loadLibrary("c", DeprecatedCLibrary.class);
        DeprecatedCLibrary synchronizedLibrary = (DeprecatedCLibrary) Native.synchronizedLibrary(library);

        assertThat(library.atol("41")).isEqualTo(41);
        assertThat(synchronizedLibrary.atol("42")).isEqualTo(42);
    }

    @Test
    void readsOptionsFromPublicLibraryFields() {
        Map<String, Object> options = Native.getLibraryOptions(FieldOptionsLibrary.class);

        assertThat(options).containsEntry(Library.OPTION_TYPE_MAPPER, FieldOptionsLibrary.TYPE_MAPPER);
        assertThat(options).containsEntry(Library.OPTION_STRUCTURE_ALIGNMENT, Structure.ALIGN_NONE);
        assertThat(options).containsEntry(Library.OPTION_STRING_ENCODING, StandardCharsets.UTF_8.name());
    }

    @Test
    void initializesPublicLibraryInstanceWhileResolvingOptions() {
        Map<String, Object> options = Native.getLibraryOptions(InstanceOptionsLibrary.class);

        assertThat(options).containsEntry(Library.OPTION_STRING_ENCODING, StandardCharsets.UTF_8.name());
        assertThat(InstanceOptionsLibrary.INSTANCE.atol("42")).isEqualTo(42);
    }

    @Test
    void registersDirectMappedOuterClassFromNestedInitializer() {
        DirectMappedOuter.Registrar.ensureRegistered();

        assertThat(DirectMappedOuter.atol("42")).isEqualTo(42);
    }

    @Test
    void queriesWebStartLibraryPathWhenWebStartPropertyIsSet() {
        String previousVersion = System.getProperty("javawebstart.version");
        try {
            System.setProperty("javawebstart.version", "test");

            assertThat(Native.getWebStartLibraryPath("c")).isNull();
        } finally {
            restoreProperty("javawebstart.version", previousVersion);
        }
    }

    @Test
    void extractsLibraryResourceUsingUnprefixedFallback() throws IOException {
        File libraryFile = File.createTempFile("jna-resource-probe", ".so");
        libraryFile.deleteOnExit();
        ClassLoader loader = new FallbackResourceClassLoader(libraryFile.toURI().toURL());

        File extracted = Native.extractFromResourcePath("resourceprobe", loader);

        assertThat(extracted).isEqualTo(libraryFile);
    }

    @Test
    void reportsMissingAbsoluteJnaResourceAfterFallbackLookup() {
        String missingResource = "/com/sun/jna/" + Platform.RESOURCE_PREFIX + "/libmissing-resource-probe.so";

        assertThatIOException()
                .isThrownBy(() -> Native.extractFromResourcePath(missingResource, new EmptyResourceClassLoader()))
                .withMessageContaining("Native library");
    }

    private static void restoreProperty(String name, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, previousValue);
        }
    }

    private static void configureDarwinResourcePrefix() {
        try {
            String mappedDispatchName = System.mapLibraryName("jnidispatch").replace(".dylib", ".jnilib");
            String dispatchResource = "com/sun/jna/" + currentResourcePrefix() + "/" + mappedDispatchName;
            Path bootDirectory = Files.createTempDirectory("jna-boot-library");
            Path dispatchLibrary = bootDirectory.resolve(mappedDispatchName);
            try (InputStream inputStream = NativeTest.class.getClassLoader().getResourceAsStream(dispatchResource)) {
                if (inputStream == null) {
                    throw new IllegalStateException("JNA dispatch resource not found: " + dispatchResource);
                }
                Files.copy(inputStream, dispatchLibrary);
            }
            dispatchLibrary.toFile().deleteOnExit();
            bootDirectory.toFile().deleteOnExit();
            System.setProperty("jna.boot.library.path", bootDirectory.toString());
            System.setProperty("jna.prefix", DARWIN_RESOURCE_PREFIX);
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static String currentResourcePrefix() {
        String osName = System.getProperty("os.name");
        String arch = canonicalArchitecture(System.getProperty("os.arch"));
        if (osName.startsWith("Linux")) {
            return "linux-" + arch;
        }
        if (osName.startsWith("Mac") || osName.startsWith("Darwin")) {
            return "darwin-" + arch;
        }
        if (osName.startsWith("Windows")) {
            return "win32-" + arch;
        }
        String osPrefix = osName.toLowerCase();
        int space = osPrefix.indexOf(' ');
        if (space != -1) {
            osPrefix = osPrefix.substring(0, space);
        }
        return osPrefix + "-" + arch;
    }

    private static String canonicalArchitecture(String arch) {
        String canonicalArch = arch.toLowerCase().trim();
        if ("powerpc".equals(canonicalArch)) {
            return "ppc";
        }
        if ("powerpc64".equals(canonicalArch)) {
            return "ppc64";
        }
        if ("i386".equals(canonicalArch) || "i686".equals(canonicalArch)) {
            return "x86";
        }
        if ("x86_64".equals(canonicalArch) || "amd64".equals(canonicalArch)) {
            return "x86-64";
        }
        if ("zarch_64".equals(canonicalArch)) {
            return "s390x";
        }
        return canonicalArch;
    }

    public interface DeprecatedCLibrary extends Library {
        int atol(String value);
    }

    public interface FieldOptionsLibrary extends Library {
        Map<String, Object> OPTIONS = Collections.emptyMap();
        TypeMapper TYPE_MAPPER = new NoopTypeMapper();
        Integer STRUCTURE_ALIGNMENT = Structure.ALIGN_NONE;
        String STRING_ENCODING = StandardCharsets.UTF_8.name();
    }

    public interface InstanceOptionsLibrary extends Library {
        Map<String, Object> LOAD_OPTIONS = NativeTest.loadOptions();
        InstanceOptionsLibrary INSTANCE = Native.load("c", InstanceOptionsLibrary.class, LOAD_OPTIONS);

        int atol(String value);
    }

    public static class DirectMappedOuter {
        public static native int atol(String value);

        public static class Registrar {
            static {
                Native.register("c");
            }

            public static void ensureRegistered() {
            }
        }
    }

    private static Map<String, Object> loadOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put(Library.OPTION_STRING_ENCODING, StandardCharsets.UTF_8.name());
        return options;
    }

    private static final class NoopTypeMapper implements TypeMapper {
        @Override
        public FromNativeConverter getFromNativeConverter(Class<?> javaType) {
            return null;
        }

        @Override
        public ToNativeConverter getToNativeConverter(Class<?> javaType) {
            return null;
        }
    }

    private static final class FallbackResourceClassLoader extends ClassLoader {
        private final URL libraryUrl;

        private FallbackResourceClassLoader(URL libraryUrl) {
            super(NativeTest.class.getClassLoader());
            this.libraryUrl = libraryUrl;
        }

        @Override
        public URL getResource(String name) {
            if (name.contains("/") || !name.contains("resourceprobe")) {
                return null;
            }
            return libraryUrl;
        }
    }

    private static final class EmptyResourceClassLoader extends ClassLoader {
        private EmptyResourceClassLoader() {
            super(NativeTest.class.getClassLoader());
        }

        @Override
        public URL getResource(String name) {
            return null;
        }
    }
}
