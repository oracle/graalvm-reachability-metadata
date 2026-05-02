/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.io.IOException;

import org.aspectj.weaver.tools.PointcutExpression;
import org.aspectj.weaver.tools.PointcutParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PointcutParserTest {
    private static final String LINT_PROPERTIES_RESOURCE =
            "org_aspectj/aspectjweaver/pointcut-parser-lint.properties";

    @Test
    void loadsLintPropertiesFromClasspathResource() throws IOException {
        PointcutParser parser = PointcutParser
                .getPointcutParserSupportingAllPrimitivesAndUsingSpecifiedClassloaderForResolution(
                        getClass().getClassLoader());

        parser.setLintProperties(LINT_PROPERTIES_RESOURCE);
        PointcutExpression expression = parser.parsePointcutExpression("execution(* *(..))");

        assertThat(expression.mayNeedDynamicTest()).isFalse();
    }
}
