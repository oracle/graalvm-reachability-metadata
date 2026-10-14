/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.javacpp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.annotation.Platform;
import org.bytedeco.javacpp.annotation.Properties;
import org.bytedeco.javacpp.tools.InfoMap;
import org.bytedeco.javacpp.tools.InfoMapper;
import org.bytedeco.javacpp.tools.Logger;
import org.bytedeco.javacpp.tools.Parser;

public class ParserTest {

    public static class ParentMapper implements InfoMapper {
        private static boolean mapped;

        @Override
        public void map(InfoMap infoMap) {
            mapped = true;
        }
    }

    @Properties(
            inherit = ParentMapper.class,
            target = "org_bytedeco.javacpp.generated",
            global = "Bindings",
            value = @Platform(include = "fixture.h"))
    public static class FixtureMapper implements InfoMapper {
        private static boolean mapped;

        @Override
        public void map(InfoMap infoMap) {
            mapped = true;
        }
    }

    @TempDir
    Path outputDirectory;

    @Test
    public void parseInstantiatesInheritedAndLeafInfoMappers() throws Exception {
        ParentMapper.mapped = false;
        FixtureMapper.mapped = false;
        Path includeDirectory = outputDirectory.resolve("org_bytedeco/javacpp/generated");
        Files.createDirectories(includeDirectory);
        Files.writeString(
                includeDirectory.resolve("fixture.h"),
                "struct SampleRecord { int value; };\n");
        Parser parser = new Parser(Logger.create(ParserTest.class), Loader.loadProperties());

        File[] generated = parser.parse(
                outputDirectory.toFile(),
                new String[] {outputDirectory.toString()},
                FixtureMapper.class);

        assertThat(ParentMapper.mapped).isTrue();
        assertThat(FixtureMapper.mapped).isTrue();
        assertThat(generated).isNotEmpty();
        assertThat(Files.readString(generated[0].toPath())).contains("SampleRecord");
    }
}
