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

public class BinaryOperationExpressionTest {

    @Test
    void renderNestedBinaryOperations() {
        TemplateEngine templateEngine = new TemplateEngine();
        Context context = new Context();
        context.setVariable("left", 2);
        context.setVariable("right", 3);

        String template = "<div>"
                + "<span th:text=\"${left + right}\">sum</span>"
                + "<span th:text=\"${(left + right) eq 5}\">equals</span>"
                + "<span th:text=\"${(left * right) gt 5 and (right - left) eq 1}\">logic</span>"
                + "</div>";

        String output = templateEngine.process(template, context);

        assertThat(output).isEqualTo("<div><span>5</span><span>true</span><span>true</span></div>");
    }
}
