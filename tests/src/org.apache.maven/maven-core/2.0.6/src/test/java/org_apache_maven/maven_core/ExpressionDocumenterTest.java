/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.usability.plugin.Expression;
import org.apache.maven.usability.plugin.ExpressionDocumentation;
import org.apache.maven.usability.plugin.ExpressionDocumenter;
import org.apache.maven.usability.plugin.io.xpp3.ParamdocXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ExpressionDocumenterTest {
    private static final List<String> EXPRESSION_DOCUMENTATION_RESOURCES = List.of(
            "META-INF/maven/plugin-expressions/project.paramdoc.xml",
            "META-INF/maven/plugin-expressions/settings.paramdoc.xml",
            "META-INF/maven/plugin-expressions/rootless.paramdoc.xml");

    @Test
    void loadsBundledExpressionDocumentation() throws IOException, XmlPullParserException {
        Map<String, Expression> documentation = loadBundledExpressionDocumentation();

        assertThat(documentation).containsKeys("project.artifact", "settings.offline", "localRepository");
        assertThat(documentation.get("project.artifact")).isNotNull();
        assertThat(documentation.get("project.artifact").getDescription()).isNotBlank();
    }

    private static Map<String, Expression> loadBundledExpressionDocumentation()
            throws IOException, XmlPullParserException {
        ClassLoader classLoader = ExpressionDocumenter.class.getClassLoader();
        ParamdocXpp3Reader reader = new ParamdocXpp3Reader();
        Map<String, Expression> documentation = new LinkedHashMap<>();

        for (String resourceName : EXPRESSION_DOCUMENTATION_RESOURCES) {
            InputStream resourceStream = classLoader.getResourceAsStream(resourceName);

            assertThat(resourceStream).as(resourceName).isNotNull();

            try (InputStream inputStream = resourceStream;
                    InputStreamReader inputReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                ExpressionDocumentation parsedDocumentation = reader.read(inputReader, true);

                if (parsedDocumentation.getExpressions() == null) {
                    continue;
                }

                for (Object parsedExpression : parsedDocumentation.getExpressions()) {
                    Expression expression = (Expression) parsedExpression;
                    documentation.put(expression.getSyntax(), expression);
                }
            }
        }

        return documentation;
    }
}
