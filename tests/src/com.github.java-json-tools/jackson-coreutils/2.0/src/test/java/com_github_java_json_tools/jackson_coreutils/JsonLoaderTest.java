/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_java_json_tools.jackson_coreutils;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonLoaderTest {
    private static final String RESOURCE_NAME = "jsonloader-context-resource.json";

    @Test
    public void shouldLoadJsonResourceFromContextClassLoaderFallback() throws IOException {
        JsonNode node = JsonLoader.fromResource("///" + RESOURCE_NAME);

        assertThat(node.get("origin").textValue()).isEqualTo("context");
        assertThat(node.get("count").intValue()).isEqualTo(2);
    }
}
