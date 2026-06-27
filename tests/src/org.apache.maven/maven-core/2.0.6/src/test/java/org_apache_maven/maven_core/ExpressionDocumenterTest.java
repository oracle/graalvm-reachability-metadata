/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import java.util.Map;

import org.apache.maven.usability.plugin.ExpressionDocumentationException;
import org.apache.maven.usability.plugin.ExpressionDocumenter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExpressionDocumenterTest {
    @Test
    void loadsBundledExpressionDocumentation() throws ExpressionDocumentationException {
        Map<?, ?> documentation = ExpressionDocumenter.load();

        assertThat(documentation.containsKey("project.artifact")).isTrue();
        assertThat(documentation.containsKey("settings.offline")).isTrue();
        assertThat(documentation.containsKey("localRepository")).isTrue();
        assertThat(documentation.get("project.artifact")).isNotNull();
    }
}
