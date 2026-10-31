/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy_yaml;

import groovy.yaml.YamlSlurper;
import org.apache.groovy.yaml.util.YamlConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(180)
public class YamlConverterTest {
    @Test
    void convertsYamlMappingToJson() {
        String json = YamlConverter.convertYamlToJson(new StringReader("""
                name: warehouse
                count: 12
                active: true
                labels: [north, south]
                optional: null
                """));

        assertThat(json).isEqualTo("""
                {"name":"warehouse","count":12,"active":true,"labels":["north","south"],"optional":null}""");
    }

    @Test
    void convertsYamlDocumentStreamToJsonArray() {
        String json = YamlConverter.convertYamlToJson(new StringReader("""
                ---
                id: 1
                ---
                id: 2
                """));

        assertThat(json).isEqualTo("""
                [{"id":1},{"id":2}]""");
    }

    @Test
    void convertsJsonToYaml() {
        String yaml = YamlConverter.convertJsonToYaml(new StringReader("""
                {"service":{"name":"warehouse","replicas":3},"active":true,"zones":["east","west"]}
                """));

        assertThat(yaml).startsWith("---\n");
        assertThat(new YamlSlurper().parseText(yaml)).isEqualTo(Map.of(
                "service", Map.of("name", "warehouse", "replicas", 3),
                "active", true, "zones", List.of("east", "west")));
    }

    @Test
    void convertsRootJsonArrayToYamlSequence() {
        String yaml = YamlConverter.convertJsonToYaml(new StringReader("""
                ["north",4,false]
                """));

        assertThat(new YamlSlurper().parseText(yaml)).isEqualTo(List.of("north", 4, false));
    }
}
