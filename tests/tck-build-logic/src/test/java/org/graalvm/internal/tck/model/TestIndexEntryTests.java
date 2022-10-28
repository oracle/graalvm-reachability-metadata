package org.graalvm.internal.tck.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TestIndexEntry}.
 *
 * @author Moritz Halbritter
 */
class TestIndexEntryTests {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserialize() throws JsonProcessingException {
        TestIndexEntry entry = objectMapper.readValue(
                """
                          {
                              "test-project-path": "org.eclipse.jetty/jetty-server/11.0.12",
                              "libraries": [
                                {
                                  "name": "org.eclipse.jetty:jetty-server",
                                  "versions": [
                                    "11.0.12"
                                  ]
                                }
                              ]
                            }
                        """, TestIndexEntry.class);

        assertThat(entry.testProjectPath()).isEqualTo("org.eclipse.jetty/jetty-server/11.0.12");
        assertThat(entry.libraries()).hasSize(1);
        TestIndexEntry.LibraryEntry libraryEntry = entry.libraries().get(0);
        assertThat(libraryEntry.name()).isEqualTo("org.eclipse.jetty:jetty-server");
        assertThat(libraryEntry.versions()).containsExactly("11.0.12");
    }
}
