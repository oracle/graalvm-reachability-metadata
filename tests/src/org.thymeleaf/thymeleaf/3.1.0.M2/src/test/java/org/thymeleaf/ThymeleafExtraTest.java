/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.thymeleaf;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.extras.java8time.dialect.Java8TimeDialect;

import static org.assertj.core.api.Assertions.assertThat;

public class ThymeleafExtraTest {

    @Test
    void renderDatesExpression() {
        TemplateEngine templateEngine = new TemplateEngine();
        templateEngine.addDialect(new Java8TimeDialect());
        Context context = new Context();
        context.setVariable("localDateTime", LocalDateTime.of(1981, 6, 15, 0, 0));
        String template = "<p th:text=\"${#temporals.format(localDateTime, 'dd/MM/yyyy')}\"></p>";
        String output = templateEngine.process(template, context);
        assertThat(output).startsWith("<p>15/06/1981</p>");
    }
}
