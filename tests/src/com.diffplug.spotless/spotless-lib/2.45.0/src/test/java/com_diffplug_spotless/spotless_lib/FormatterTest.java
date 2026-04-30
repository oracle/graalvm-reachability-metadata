/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_diffplug_spotless.spotless_lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.diffplug.spotless.FormatExceptionPolicy;
import com.diffplug.spotless.Formatter;
import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.LineEnding;

public class FormatterTest {
    @Test
    void serializesAndDeserializesFormatterConfiguration() throws Exception {
        final Path rootDir = Files.createTempDirectory("spotless-formatter-");
        rootDir.toFile().deleteOnExit();
        final Formatter formatter = Formatter.builder()
                .name("configured formatter")
                .lineEndingsPolicy(LineEnding.UNIX.createPolicy())
                .encoding(StandardCharsets.UTF_8)
                .rootDir(rootDir)
                .steps(List.of(new AppendSuffixStep("!")))
                .exceptionPolicy(FormatExceptionPolicy.failOnlyOnError())
                .build();

        final byte[] serialized = serialize(formatter);
        final Formatter restored = deserialize(serialized);

        assertThat(restored.getName()).isEqualTo(formatter.getName());
        assertThat(restored.getLineEndingsPolicy()).isEqualTo(formatter.getLineEndingsPolicy());
        assertThat(restored.getEncoding()).isEqualTo(formatter.getEncoding());
        assertThat(restored.getRootDir()).isEqualTo(formatter.getRootDir());
        assertThat(restored.getSteps()).isEqualTo(formatter.getSteps());
        assertThat(restored.getExceptionPolicy()).isEqualTo(formatter.getExceptionPolicy());
        assertThat(restored.compute("value", Formatter.NO_FILE_SENTINEL)).isEqualTo("value!");
    }

    private static byte[] serialize(Formatter formatter) throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream outputStream = new ObjectOutputStream(bytes)) {
            outputStream.writeObject(formatter);
        }
        return bytes.toByteArray();
    }

    private static Formatter deserialize(byte[] serialized) throws Exception {
        try (ObjectInputStream inputStream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return (Formatter) inputStream.readObject();
        }
    }

    private static final class AppendSuffixStep implements FormatterStep {
        private static final long serialVersionUID = 1L;

        private final String suffix;

        private AppendSuffixStep(String suffix) {
            this.suffix = Objects.requireNonNull(suffix, "suffix");
        }

        @Override
        public String getName() {
            return "append suffix";
        }

        @Override
        public String format(String rawUnix, File file) {
            return rawUnix + suffix;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other == null || getClass() != other.getClass()) {
                return false;
            }
            final AppendSuffixStep that = (AppendSuffixStep) other;
            return suffix.equals(that.suffix);
        }

        @Override
        public int hashCode() {
            return suffix.hashCode();
        }
    }
}
