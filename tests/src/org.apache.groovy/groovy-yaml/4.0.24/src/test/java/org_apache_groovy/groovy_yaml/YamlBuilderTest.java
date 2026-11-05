/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_yaml;

import groovy.yaml.YamlBuilder;
import groovy.yaml.YamlSlurper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(180)
public class YamlBuilderTest {
    @Test
    void buildsNestedMapping() {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("name", "catalog");
        content.put("enabled", true);
        content.put("connection", Map.of("host", "localhost", "port", 8080));
        content.put("tags", List.of("public", "stable"));
        content.put("optional", null);
        YamlBuilder builder = new YamlBuilder();

        assertThat(builder.call(content)).isEqualTo(content);
        assertThat(builder.getContent()).isEqualTo(content);
        assertThat(new YamlSlurper().parseText(builder.toString())).isEqualTo(content);
    }

    @Test
    void buildsRootList() {
        List<Object> content = List.of(Map.of("name", "first"), Map.of("name", "second"));
        YamlBuilder builder = new YamlBuilder();

        assertThat(builder.call(content)).isEqualTo(content);
        assertThat(builder.getContent()).isEqualTo(content);
        assertThat(new YamlSlurper().parseText(builder.toString())).isEqualTo(content);
    }

    @Test
    void buildsVarargsSequenceWithNull() {
        YamlBuilder builder = new YamlBuilder();
        List<Object> expected = Arrays.asList("ready", 7, true, null);

        assertThat(builder.call("ready", 7, true, null)).isEqualTo(expected);
        assertThat(builder.getContent()).isEqualTo(expected);
        assertThat(new YamlSlurper().parseText(builder.toString())).isEqualTo(expected);
    }

    @Test
    void writesYamlToSuppliedWriter() throws Exception {
        YamlBuilder builder = new YamlBuilder();
        builder.call(Map.of("message", "line one\nline two", "values", List.of(3, 5)));
        StringWriter writer = new StringWriter();
        writer.append("# generated configuration\n");

        Writer returned = builder.writeTo(writer);

        assertThat(returned).isSameAs(writer);
        assertThat(writer.toString()).startsWith("# generated configuration\n---\n");
        assertThat(new YamlSlurper().parseText(writer.toString())).isEqualTo(
                Map.of("message", "line one\nline two", "values", List.of(3, 5)));
    }
}
