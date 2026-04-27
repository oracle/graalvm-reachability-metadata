/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import org.junit.jupiter.api.Test;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import static org.assertj.core.api.Assertions.assertThat;

class OGNLVariableExpressionEvaluatorThymeleafDefaultClassResolverTest {

    @Test
    void resolvesAllowedJavaTypeForStaticMethodInvocation() {
        TemplateEngine templateEngine = new TemplateEngine();
        Context context = new Context();

        String output = templateEngine.process(
                "<p th:text=\"${@java.lang.Math@max(2, 5)}\">placeholder</p>",
                context);

        assertThat(output).isEqualTo("<p>5</p>");
    }
}
