/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.javacpp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.bytedeco.javacpp.FunctionPointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.annotation.Platform;
import org.bytedeco.javacpp.annotation.StdString;
import org.bytedeco.javacpp.tools.Generator;
import org.bytedeco.javacpp.tools.Logger;

public class GeneratorTest {

    @Platform(include = "<string>", library = "javacpp_generator_test")
    public static class GeneratedBindings extends Pointer {
        public GeneratedBindings() { }

        public GeneratedBindings(Pointer pointer) {
            super(pointer);
        }

        public native void consume(
                @StdString String text, NativeCode code, NativeCallback callback);

        public static class NestedPointer extends Pointer {
            public NestedPointer() { }

            public NestedPointer(Pointer pointer) {
                super(pointer);
            }

            public native int value();
        }

        public static class NativeCallback extends FunctionPointer {
            public NativeCallback() { }

            public native int call(int value);
        }

        public enum NativeCode {
            FIRST(1),
            SECOND(2);

            public final int value;

            NativeCode(int value) {
                this.value = value;
            }
        }
    }

    @TempDir
    Path outputDirectory;

    @Test
    public void generateInspectsBindingsCallbacksAdaptersAndEnums() throws Exception {
        Path source = outputDirectory.resolve("generated.cpp");
        Generator generator = new Generator(
                Logger.create(GeneratorTest.class), Loader.loadProperties());

        boolean generated = generator.generate(
                source.toString(), null, null, null, "", null, null,
                GeneratedBindings.class);

        assertThat(generated).isTrue();
        assertThat(source).exists();
        assertThat(Files.readString(source))
                .contains("consume", "NativeCallback", "StringAdapter");
    }
}
