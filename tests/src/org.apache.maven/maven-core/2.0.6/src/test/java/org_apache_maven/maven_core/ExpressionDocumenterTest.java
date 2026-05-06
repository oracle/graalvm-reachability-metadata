/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import org.apache.maven.usability.plugin.Expression;
import org.apache.maven.usability.plugin.ExpressionDocumentation;
import org.apache.maven.usability.plugin.ExpressionDocumenter;
import org.apache.maven.usability.plugin.io.xpp3.ParamdocXpp3Reader;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExpressionDocumenterTest {
    private static final List<String> EXPRESSION_DOCUMENTATION_RESOURCES = List.of(
            "META-INF/maven/plugin-expressions/project.paramdoc.xml",
            "META-INF/maven/plugin-expressions/rootless.paramdoc.xml",
            "META-INF/maven/plugin-expressions/settings.paramdoc.xml");

    @Test
    public void loadReadsBundledExpressionDocumentation() throws Exception {
        new ExpressionDocumenter();
        ClassLoader classLoader = ExpressionDocumenter.class.getClassLoader();
        String classResourceName = ExpressionDocumenter.class.getName().replace('.', '/') + ".class";
        URL classResource = classLoader.getResource(classResourceName);

        assertNotNull(classResource);

        Map<?, ?> documentation = "resource".equals(classResource.getProtocol())
                ? loadDocumentationFromRegisteredResources(classLoader)
                : ExpressionDocumenter.load();

        assertHasExpectedEntries(documentation);
    }

    private static Map<String, Expression> loadDocumentationFromRegisteredResources(ClassLoader classLoader) throws Exception {
        Map<String, Expression> documentation = new HashMap<>();
        ParamdocXpp3Reader reader = new ParamdocXpp3Reader();

        // Native image exposes registered resources via the resource: scheme, which is not compatible
        // with ExpressionDocumenter's jar/file path reconstruction.
        for (String resourceName : EXPRESSION_DOCUMENTATION_RESOURCES) {
            try (InputStream stream = classLoader.getResourceAsStream(resourceName)) {
                assertNotNull(stream, resourceName);
                try (BufferedReader bufferedReader = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    ExpressionDocumentation expressionDocumentation = reader.read(bufferedReader, true);
                    if (expressionDocumentation.getExpressions() != null) {
                        for (Object expressionObject : expressionDocumentation.getExpressions()) {
                            Expression expression = (Expression) expressionObject;
                            documentation.put(expression.getSyntax(), expression);
                        }
                    }
                }
            }
        }

        return documentation;
    }

    private static void assertHasExpectedEntries(Map<?, ?> documentation) {
        assertNotNull(documentation);
        assertFalse(documentation.isEmpty());
        assertTrue(documentation.containsKey("project.artifact"));
        assertTrue(documentation.containsKey("settings.offline"));
        assertTrue(documentation.containsKey("localRepository"));
    }
}
