/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import static org.assertj.core.api.Assertions.assertThat;

import org.aspectj.weaver.tools.PointcutExpression;
import org.aspectj.weaver.tools.PointcutParser;
import org.junit.jupiter.api.Test;

public class PointcutParserTest {
    private static final String LINT_PROPERTIES_RESOURCE = "org_aspectj/aspectjweaver/pointcut-parser-lint.properties";

    @Test
    void loadsLintPropertiesFromClasspathResource() throws Exception {
        PointcutParser parser = PointcutParser.getPointcutParserSupportingAllPrimitivesAndUsingSpecifiedClassloaderForResolution(
                PointcutParserTest.class.getClassLoader());

        parser.setLintProperties(LINT_PROPERTIES_RESOURCE);
        PointcutExpression expression = parser.parsePointcutExpression("within(java.lang.String)");

        assertThat(expression.getPointcutExpression()).isEqualTo("within(java.lang.String)");
        assertThat(expression.couldMatchJoinPointsInType(String.class)).isTrue();
        assertThat(expression.couldMatchJoinPointsInType(Integer.class)).isFalse();
    }
}
