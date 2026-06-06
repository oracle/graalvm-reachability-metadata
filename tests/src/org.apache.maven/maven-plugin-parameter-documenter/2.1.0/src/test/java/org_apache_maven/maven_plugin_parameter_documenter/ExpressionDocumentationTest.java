/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_plugin_parameter_documenter;

import org.apache.maven.usability.plugin.ExpressionDocumentation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class ExpressionDocumentationTest {
    @Test
    void rejectsNullExpressionWhenAddingToDocumentation() {
        ExpressionDocumentation documentation = new ExpressionDocumentation();

        assertThatExceptionOfType(ClassCastException.class)
                .isThrownBy(() -> documentation.addExpression(null))
                .withMessageContaining("ExpressionDocumentation.addExpressions(expression) parameter must be instanceof")
                .withMessageContaining("org.apache.maven.usability.plugin.Expression");
    }
}
