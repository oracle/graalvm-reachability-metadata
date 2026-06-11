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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

public class NativeTest {
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
