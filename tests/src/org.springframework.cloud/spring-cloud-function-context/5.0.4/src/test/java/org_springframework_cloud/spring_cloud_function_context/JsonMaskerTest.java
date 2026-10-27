/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_cloud.spring_cloud_function_context;

import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.function.utils.JsonMasker;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonMaskerTest {
    @Test
    void instanceLoadsMaskKeyResourcesBeforeMaskingConfiguredKeys() {
        JsonMasker jsonMasker = JsonMasker.INSTANCE(Set.of("password", "token"));

        String maskedJson = jsonMasker.mask("""
                {
                  "password": "secret",
                  "profile": {
                    "token": "abc123",
                    "name": "Ada"
                  },
                  "items": [
                    {"password": "nested-secret"}
                  ]
                }
                """);

        assertThat(maskedJson).containsPattern(maskedValuePattern("password"));
        assertThat(maskedJson).containsPattern(maskedValuePattern("token"));
        assertThat(maskedJson).containsPattern("\\\"name\\\"\\s*:\\s*\\\"Ada\\\"");
        assertThat(maskedJson).doesNotContain("secret", "abc123", "nested-secret");
    }

    private static String maskedValuePattern(String key) {
        return "\\\"" + key + "\\\"\\s*:\\s*\\\"\\*\\*\\*\\*\\*\\*\\*\\\"";
    }
}
