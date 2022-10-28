package org.graalvm.internal.tck.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link MetadataIndexEntry}.
 *
 * @author Moritz Halbritter
 */
class MetadataIndexEntryTests {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializeWithDirectory() throws JsonProcessingException {
        MetadataIndexEntry entry = objectMapper.readValue(
                """
                          {
                            "directory": "org.jline/jline",
                            "module": "org.jline:jline"
                          }            
                        """, MetadataIndexEntry.class);

        assertThat(entry.module()).isEqualTo("org.jline:jline");
        assertThat(entry.directory()).isEqualTo("org.jline/jline");
        assertThat(entry.requires()).isNull();
    }

    @Test
    void deserializeWithRequires() throws JsonProcessingException {
        MetadataIndexEntry entry = objectMapper.readValue(
                """
                        {
                          "module": "org.jline:jline-console",
                          "requires": [
                            "org.jline:jline"
                          ]
                        }
                        """, MetadataIndexEntry.class);

        assertThat(entry.module()).isEqualTo("org.jline:jline-console");
        assertThat(entry.directory()).isNull();
        assertThat(entry.requires()).containsExactly("org.jline:jline");
    }
}
