/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import java.awt.Canvas;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.sun.jna.Function;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import com.sun.jna.Platform;
import com.sun.jna.Structure;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FunctionNativeCoverageTest {
    public interface Posix extends Library {
        int atol(String value);
        int strlen(String value);
        long labs(long value);
        double atof(String value);
        Pointer strchr(String value, int character);
        Pointer strerror(int error);
        DivisionResultByValue div(int numerator, int denominator);
        int puts(String value);
    }

    public interface VarargsPosix extends Library {
        int printf(String format, Object... arguments);
    }

    @Structure.FieldOrder({"quotient", "remainder"})
    public static class DivisionResult extends Structure {
        public int quotient;
        public int remainder;
    }

    public static class DivisionResultByValue extends DivisionResult implements Structure.ByValue {
    }

    @Structure.FieldOrder({"integer", "floating"})
    public static class MixedUnion extends com.sun.jna.Union {
        public int integer;
        public double floating;
    }

    public static class MixedUnionByValue extends MixedUnion implements Structure.ByValue {
    }

    public interface ConfiguredLibrary extends Library {
        ConfiguredLibrary INSTANCE = Native.load("c", ConfiguredLibrary.class);

        interface NestedCallback extends com.sun.jna.Callback {
            void callback();
        }
    }

    public static final class RegisteredStructures {
        public static native int atol(StructureCoverageTest.RecordByValue value);

        private RegisteredStructures() {
        }

        static void register() {
            Native.register("c");
        }

        static void unregister() {
            Native.unregister();
        }
    }

    private static final Posix POSIX = Native.load("c", Posix.class);

    public static final class RegisteredByName {
        public static native int atol(String value);

        private RegisteredByName() {
        }

        static void register() {
            Native.register("c");
        }

        static void unregister() {
            Native.unregister();
        }
    }

    public static final class RegisteredMixedUnion {
        public static native int abs(MixedUnionByValue value);

        private RegisteredMixedUnion() {
        }

        static void register() {
            Native.register("c");
        }

        static void unregister() {
            Native.unregister();
        }
    }

    public static final class RegisteredByLibrary {
        public static native int strlen(String value);

        private RegisteredByLibrary() {
        }

        static void register(NativeLibrary library) {
            Native.register(library);
        }

        static void unregister() {
            Native.unregister();
        }
    }

    @Test
    void functionLookupAndTypedInvocation() {
        NativeLibrary library = NativeLibrary.getInstance("c");
        Function function = library.getFunction("atol");
        assertThat(function.getName()).isEqualTo("atol");
        assertThat(function.getCallingConvention()).isEqualTo(Function.C_CONVENTION);
        assertThat(function.toString()).contains("atol");
        assertThat(function.hashCode()).isEqualTo(function.hashCode());
        assertThat(function).isEqualTo(Function.getFunction("c", "atol"));

        Function byName = Function.getFunction("c", "atol");
        Function byFlags = Function.getFunction("c", "atol", Function.C_CONVENTION);
        Function byEncoding = Function.getFunction("c", "atol", Function.C_CONVENTION, "UTF-8");
        Function byPointer = Function.getFunction(function);
        Function byPointerFlags = Function.getFunction(function, Function.C_CONVENTION);
        Function byPointerEncoding = Function.getFunction(function, Function.C_CONVENTION, "UTF-8");
        assertThat(byName.invokeInt(new Object[]{"42"})).isEqualTo(42);
        assertThat(byFlags.invokeInt(new Object[]{"43"})).isEqualTo(43);
        assertThat(byEncoding.invokeInt(new Object[]{"44"})).isEqualTo(44);
        assertThat(byPointer.invokeInt(new Object[]{"45"})).isEqualTo(45);
        assertThat(byPointerFlags.invokeInt(new Object[]{"46"})).isEqualTo(46);
        assertThat(byPointerEncoding.invokeInt(new Object[]{"47"})).isEqualTo(47);
        assertThat(byPointerEncoding.equals(byPointerFlags)).isTrue();

        assertThat(function.invoke(int.class, new Object[]{"48"})).isEqualTo(48);
        assertThat(function.invoke(int.class, new Object[]{"49"}, Collections.emptyMap())).isEqualTo(49);
        assertThat(Function.getFunction("c", "strlen")
                .invoke(int.class, new Object[]{"find me"})).isEqualTo(7);
        assertThat(Function.getFunction("c", "atoi")
                .invoke(Integer.class, new Object[]{"57"})).isEqualTo(57);
        // Pointer-returning native interface calls are exercised by the JVM lane.
        // The corresponding native-image bridge is not stable on this JNA release.
        if (System.getProperty("org.graalvm.nativeimage.imagecode") == null) {
            assertThat(POSIX.strchr("find me", 'd')).isNotNull();
            Function strerror = Function.getFunction("c", "strerror");
            assertThat(strerror.invokePointer(new Object[]{0})).isNotNull();
            assertThat(strerror.invokeString(new Object[]{0}, false)).isNotEmpty();
            DivisionResultByValue result = POSIX.div(17, 5);
            assertThat(result.quotient).isEqualTo(3);
            assertThat(result.remainder).isEqualTo(2);
        }
        try {
            Map<String, Object> invokingMethod = new HashMap<>();
            invokingMethod.put("invoking-method", Posix.class.getMethod("atol", String.class));
            assertThat(function.invoke(int.class, new Object[]{"55"}, invokingMethod)).isEqualTo(55);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
        assertThat(POSIX.atol("57")).isEqualTo(57);
        assertThat(function.invokeInt(new Object[]{"50"})).isEqualTo(50);
        assertThat(POSIX.strlen("hello")).isEqualTo(5);
        assertThat(Function.getFunction("c", "abs").invokeInt(new Object[]{-9})).isEqualTo(9);
        assertThat(Function.getFunction("c", "atoi").invokeInt(new Object[]{"56"})).isEqualTo(56);
        assertThat(Function.getFunction("c", "getpid").invokeInt(new Object[0])).isPositive();
        Function puts = Function.getFunction("c", "puts");
        assertThat(puts.invokeInt(new Object[]{"JNA coverage"})).isGreaterThan(0);
        // The void-returning raw JNI bridge is unstable in the native-image
        // lane for this JNA release; retain these convenience paths in JVM
        // JaCoCo coverage without making the native sampling run crash.
        if (System.getProperty("org.graalvm.nativeimage.imagecode") == null) {
            puts.invoke(new Object[]{"JNA void coverage"});
            puts.invokeVoid(new Object[]{"JNA invokeVoid coverage"});
        }
        // The same native-image limitation affects the non-integer raw JNI
        // convenience bridges; keep their asserted JVM coverage intact.
        if (System.getProperty("org.graalvm.nativeimage.imagecode") == null) {
            assertThat(Function.getFunction("c", "labs").invokeLong(new Object[]{-42L})).isEqualTo(42L);
            assertThat(Function.getFunction("c", "atof").invokeDouble(new Object[]{"3.5"})).isEqualTo(3.5d);
            assertThat(Function.getFunction("c", "strtof")
                    .invokeFloat(new Object[]{"2.5", Pointer.NULL})).isEqualTo(2.5f);
        }
        VarargsPosix varargs = Native.load("c", VarargsPosix.class);
        assertThat(varargs.printf("%s", "JNA")).isGreaterThan(0);


        // Raw native functions do not permit Object return types unless the
        // library explicitly enables raw JNI object interaction.
        Function objectFunction = Function.getFunction("c", "atol");
        assertThatThrownBy(() -> objectFunction.invokeObject(new Object[]{"54"}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported return type");
    }

    @Test
    void functionArrayArgumentsAreReadAfterInvocation() {
        Function getpid = Function.getFunction("c", "getpid");
        Pointer[] pointers = {Pointer.createConstant(1), Pointer.NULL};
        assertThat(getpid.invoke(Integer.class, new Object[]{pointers})).isInstanceOf(Integer.class);
        ValueTypesCoverageTest.TestInteger[] integers = {
                new ValueTypesCoverageTest.TestInteger(1), new ValueTypesCoverageTest.TestInteger(2)};
        assertThat(getpid.invoke(Integer.class, new Object[]{integers})).isInstanceOf(Integer.class);
    }

    @Test
    void nativeConversionAndLibraryLoading() throws Exception {
        assertThat(Native.toByteArray("hello")).containsExactly(104, 101, 108, 108, 111, 0);
        assertThat(Native.toByteArray("hello", "UTF-8")).containsExactly(104, 101, 108, 108, 111, 0);
        assertThat(Native.toByteArray("\u00e9", StandardCharsets.UTF_8)).containsExactly(-61, -87, 0);
        assertThat(Native.toCharArray("hello")).containsExactly('h', 'e', 'l', 'l', 'o', '\0');
        byte[] bytes = {97, 98, 0, 99};
        assertThat(Native.toString(bytes)).isEqualTo("ab");
        assertThat(Native.toString(bytes, "UTF-8")).isEqualTo("ab");
        assertThat(Native.toString(bytes, StandardCharsets.UTF_8)).isEqualTo("ab");
        assertThat(Native.toString(new char[]{'a', 'b', 0, 'c'})).isEqualTo("ab");
        assertThat(Native.toStringList(new char[]{'a', 'b', 0, 'c', 0, 0})).containsExactly("ab", "c");
        assertThat(Native.toStringList(new char[]{'x', 'a', 'b', 0, 'c'}, 1, 4)).containsExactly("ab", "c");

        Map<String, Object> options = new HashMap<>();
        options.put(Library.OPTION_CALLING_CONVENTION, Function.C_CONVENTION);
        assertThat(Native.getLibraryOptions(ConfiguredLibrary.NestedCallback.class)).isNotNull();
        assertThat(Native.getWebStartLibraryPath("c")).isNull();
        String webStartVersion = System.getProperty("javawebstart.version");
        try {
            System.setProperty("javawebstart.version", "coverage");
            assertThat(Native.getWebStartLibraryPath("c")).isNull();
        } finally {
            if (webStartVersion == null) {
                System.clearProperty("javawebstart.version");
            } else {
                System.setProperty("javawebstart.version", webStartVersion);
            }
        }
        assertThat(Native.load("c", Posix.class)).isNotNull();
        assertThat(Native.load("c", Posix.class, options).atol("51")).isEqualTo(51);
        assertThat(Native.load(Posix.class)).isNotNull();
        assertThat(Native.load(Posix.class, options)).isNotNull();
        assertThat(Native.loadLibrary("c", Posix.class)).isNotNull();
        assertThat(Native.loadLibrary("c", Posix.class, options)).isNotNull();
        assertThat(Native.loadLibrary(Posix.class)).isNotNull();
        assertThat(Native.loadLibrary(Posix.class, options)).isNotNull();
        assertThat(((Posix) Native.synchronizedLibrary(POSIX)).atol("52")).isEqualTo(52);
        assertThat(Native.isSupportedNativeType(Integer.class)).isTrue();
        assertThat(Native.isSupportedNativeType(Object.class)).isFalse();
        assertThat(Native.getCallbackExceptionHandler()).isNotNull();
        Native.setCallbackExceptionHandler((callback, throwable) -> {
        });
        assertThat(Native.getCallbackExceptionHandler()).isNotNull();
        Native.detach(false);
        Native.main(new String[0]);

        File extracted = Native.extractFromResourcePath("/com/sun/jna/linux-x86-64/libjnidispatch.so");
        assertThat(extracted).exists().isFile();
        File extractedWithLoader = Native.extractFromResourcePath("/com/sun/jna/linux-x86-64/libjnidispatch.so", getClass().getClassLoader());
        assertThat(extractedWithLoader).exists().isFile();
        NativeLibrary resourceLibrary = NativeLibrary.getInstance(
                "/com/sun/jna/linux-x86-64/libjnidispatch.so");
        assertThat(resourceLibrary.getFile()).isNotNull();
        resourceLibrary.dispose();

        RegisteredByName.register();
        assertThat(RegisteredByName.atol("53")).isEqualTo(53);
        assertThat(Native.registered(RegisteredByName.class)).isTrue();
        RegisteredByName.unregister();
        assertThat(Native.registered(RegisteredByName.class)).isFalse();
        RegisteredByLibrary.register(NativeLibrary.getInstance("c"));
        assertThat(RegisteredByLibrary.strlen("abc")).isEqualTo(3);
        assertThat(Native.registered(RegisteredByLibrary.class)).isTrue();
        RegisteredByLibrary.unregister();
        Native.unregister(RegisteredByLibrary.class);
        RegisteredStructures.register();
        RegisteredStructures.unregister();
        RegisteredMixedUnion.register();
        RegisteredMixedUnion.unregister();

        assertThat(Native.getNativeSize(StructureCoverageTest.Record.class)).isGreaterThan(0);
        assertThat(Native.getNativeSize(StructureCoverageTest.Record.class, new StructureCoverageTest.Record()))
                .isGreaterThan(0);
    }

    @Test
    void nativeLibraryAccessAndAwtGuards() throws Exception {
        NativeLibrary library = NativeLibrary.getInstance("c");
        NativeLibrary withLoader = NativeLibrary.getInstance("c", getClass().getClassLoader());
        NativeLibrary withOptions = NativeLibrary.getInstance("c", Collections.emptyMap());
        NativeLibrary process = NativeLibrary.getProcess();
        NativeLibrary processWithOptions = NativeLibrary.getProcess(Collections.emptyMap());
        assertThat(library.getFunction("atol").invokeInt(new Object[]{"54"})).isEqualTo(54);
        assertThat(withLoader.getFunction("atol")).isNotNull();
        assertThat(withOptions.getFunction("atol")).isNotNull();
        assertThat(processWithOptions.getName()).isEqualTo(process.getName());
        assertThat(library.getGlobalVariableAddress("environ")).isNotNull();
        assertThat(library.toString()).isNotEmpty();
        NativeLibrary.addSearchPath("c", "/tmp");
        NativeLibrary disposable = NativeLibrary.getInstance("c", Collections.singletonMap("dispose-test", Boolean.TRUE));
        disposable.dispose();

        if (Platform.isMac()) {
            String missingFramework = "jnaCoverage" + System.nanoTime();
            assertThatThrownBy(() -> NativeLibrary.getInstance(missingFramework))
                    .isInstanceOf(UnsatisfiedLinkError.class);
        }

        Path missingLibraryDirectory = Files.createTempDirectory("jna-coverage");
        String missingLibrary = "jnaCoverage" + System.nanoTime();
        Files.createFile(missingLibraryDirectory.resolve("lib" + missingLibrary + ".so.1.2"));
        NativeLibrary.addSearchPath(missingLibrary, missingLibraryDirectory.toString());
        try {
            assertThatThrownBy(() -> NativeLibrary.getInstance(missingLibrary))
                    .isInstanceOf(UnsatisfiedLinkError.class);
        } finally {
            Files.deleteIfExists(missingLibraryDirectory.resolve("lib" + missingLibrary + ".so.1.2"));
            Files.deleteIfExists(missingLibraryDirectory);
        }

        System.setProperty("java.awt.headless", "true");
        if (GraphicsEnvironment.isHeadless()) {
            assertThatThrownBy(() -> Native.getWindowID(null)).isInstanceOf(HeadlessException.class);
            assertThatThrownBy(() -> Native.getComponentID(null)).isInstanceOf(HeadlessException.class);
            assertThatThrownBy(() -> Native.getWindowPointer(null)).isInstanceOf(HeadlessException.class);
            assertThatThrownBy(() -> Native.getComponentPointer(null)).isInstanceOf(HeadlessException.class);
        } else {
            Frame frame = new Frame();
            try {
                try {
                    assertThat(Native.getWindowID(frame)).isGreaterThanOrEqualTo(0L);
                } catch (IllegalStateException expected) {
                    assertThat(expected).hasMessageContaining("displayable");
                }
                try {
                    assertThat(Native.getWindowPointer(frame)).isNotNull();
                } catch (IllegalStateException expected) {
                    assertThat(expected).hasMessageContaining("displayable");
                }
                Canvas canvas = new Canvas();
                try {
                    assertThat(Native.getComponentID(canvas)).isGreaterThanOrEqualTo(0L);
                } catch (IllegalStateException expected) {
                    assertThat(expected).hasMessageContaining("displayable");
                }
                try {
                    assertThat(Native.getComponentPointer(canvas)).isNotNull();
                } catch (IllegalStateException expected) {
                    assertThat(expected).hasMessageContaining("displayable");
                }
            } finally {
                frame.dispose();
            }
        }
    }
}
