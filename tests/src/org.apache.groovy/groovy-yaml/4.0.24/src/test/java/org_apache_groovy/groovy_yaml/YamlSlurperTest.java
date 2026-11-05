/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_yaml;

import groovy.yaml.YamlSlurper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(180)
public class YamlSlurperTest {
    @TempDir
    Path directory;

    @Test
    void parsesNestedMappingsAndTypedScalars() {
        Object result = new YamlSlurper().parseText("""
                service:
                  name: catalog
                  port: 8080
                  enabled: true
                  weights: [1, 2.5, null]
                  tags:
                    - public
                    - stable
                """);

        assertThat(result).isEqualTo(Map.of("service", Map.of(
                "name", "catalog", "port", 8080, "enabled", true,
                "weights", Arrays.asList(1, new BigDecimal("2.5"), null),
                "tags", List.of("public", "stable"))));
    }

    @Test
    void parsesReaderWithNestedMappingAndMultilineText() {
        String yaml = """
                worker:
                  retries: 3
                  enabled: true
                description: |
                  first line
                  second line
                """;
        Object result = new YamlSlurper().parse(new StringReader(yaml));

        assertThat(result).isEqualTo(Map.of(
                "worker", Map.of("retries", 3, "enabled", true),
                "description", "first line\nsecond line\n"));
    }

    @Test
    void parsesInputStream() throws Exception {
        byte[] yaml = "items: [apple, pear]\ncount: 2\n".getBytes(StandardCharsets.UTF_8);
        try (ByteArrayInputStream stream = new ByteArrayInputStream(yaml)) {
            assertThat(new YamlSlurper().parse(stream))
                    .isEqualTo(Map.of("items", List.of("apple", "pear"), "count", 2));
        }
    }

    @Test
    void parsesPathAndFile() throws Exception {
        Path yaml = Files.writeString(directory.resolve("settings.yaml"), """
                database:
                  host: localhost
                  ports: [5432, 5433]
                """);
        Map<String, Object> expected = Map.of("database",
                Map.of("host", "localhost", "ports", List.of(5432, 5433)));
        YamlSlurper slurper = new YamlSlurper();

        assertThat(slurper.parse(yaml)).isEqualTo(expected);
        assertThat(slurper.parse(yaml.toFile())).isEqualTo(expected);
    }

    @Test
    void parsesMultipleDocumentsAsAList() {
        Object result = new YamlSlurper().parseText("""
                ---
                name: primary
                ---
                name: secondary
                """);

        assertThat(result).isEqualTo(List.of(
                Map.of("name", "primary"), Map.of("name", "secondary")));
    }
}
