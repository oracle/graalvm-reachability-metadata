/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_metadata;

import org.apache.maven.api.metadata.Plugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PluginTest {
    @Test
    void buildsAndUpdatesPluginPrefixMappings() {
        Plugin original = Plugin.newBuilder().name("Sample Compiler")
                .prefix("compile").artifactId("sample-compiler-plugin").build();
        Plugin updated = original.with().prefix("build").build();

        assertEquals("Sample Compiler", updated.getName());
        assertEquals("build", updated.getPrefix());
        assertEquals("sample-compiler-plugin", updated.getArtifactId());
        assertEquals("compile", original.getPrefix());
    }

    @Test
    void fluentCopiesReplaceOnlyTheRequestedMappingFields() {
        Plugin original = Plugin.newInstance().withName("Sample Compiler")
                .withPrefix("compile").withArtifactId("sample-compiler-plugin");
        Plugin updated = Plugin.newBuilder(original, true).build()
                .withName("Sample Reports").withPrefix("report").withArtifactId("sample-report-plugin");

        assertEquals("Sample Reports", updated.getName());
        assertEquals("report", updated.getPrefix());
        assertEquals("sample-report-plugin", updated.getArtifactId());
        assertEquals("Sample Compiler", original.getName());
        assertEquals("compile", original.getPrefix());
        assertEquals("sample-compiler-plugin", original.getArtifactId());
    }
}
