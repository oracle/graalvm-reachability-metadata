/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.graalvm.internal.tck.harness.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValidateIndexFilesTaskTests {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void detectsMavenCentralUrlTemplatesThatRenderAliasesToUnlistedArtifactVersions() throws IOException {
        JsonNode index = OBJECT_MAPPER.readTree("""
                [
                  {
                    "metadata-version": "13-ea",
                    "source-code-url": "https://repo1.maven.org/maven2/org/openjfx/javafx-base/$version$+1/javafx-base-$version$+1-sources.jar",
                    "documentation-url": "https://repo1.maven.org/maven2/org/openjfx/javafx-base/$version$+1/javafx-base-$version$+1-javadoc.jar",
                    "tested-versions": [
                      "13-ea",
                      "13-ea+1",
                      "13"
                    ]
                  }
                ]
                """);
        List<String> failures = new ArrayList<>();

        ValidateIndexFilesTask.checkLibraryIndexUrlTemplates(
                index,
                "metadata/org.openjfx/javafx-base/index.json",
                failures
        );

        assertThat(failures)
                .anySatisfy(failure -> assertThat(failure)
                        .contains("source-code-url")
                        .contains("renders tested-version 13-ea+1 to Maven artifact version 13-ea+1+1"))
                .anySatisfy(failure -> assertThat(failure)
                        .contains("documentation-url")
                        .contains("renders tested-version 13 to Maven artifact version 13+1"));
    }

    @Test
    void allowsMavenCentralUrlTemplatesThatRenderEachAliasToItsOwnArtifactVersion() throws IOException {
        JsonNode index = OBJECT_MAPPER.readTree("""
                [
                  {
                    "metadata-version": "1.0.0",
                    "source-code-url": "https://repo.maven.apache.org/maven2/com/example/demo/$version$/demo-$version$-sources.jar",
                    "documentation-url": "https://repo.maven.apache.org/maven2/com/example/demo/$version$/demo-$version$-javadoc.jar",
                    "tested-versions": [
                      "1.0.0",
                      "1.1.0"
                    ]
                  }
                ]
                """);
        List<String> failures = new ArrayList<>();

        ValidateIndexFilesTask.checkLibraryIndexUrlTemplates(
                index,
                "metadata/com.example/demo/index.json",
                failures
        );

        assertThat(failures).isEmpty();
    }
}
