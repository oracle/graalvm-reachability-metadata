/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.thymeleaf;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.SpringWebFluxTemplateEngine;

import static org.assertj.core.api.Assertions.assertThat;

public class ThymeleafSpring6Test {

    @Test
    void templateEngine() {
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        Context context = new Context();
        context.setVariable("key", "value");
        String template = "<p th:text=\"${key}\"></p>";
        String output = templateEngine.process(template, context);
        assertThat(output).isEqualTo("<p>value</p>");
    }

    @Test
    void webFluxTemplateEngine() {
        SpringWebFluxTemplateEngine templateEngine = new SpringWebFluxTemplateEngine();
        Context context = new Context();
        context.setVariable("key", "value");
        String template = "<p th:text=\"${key}\"></p>";
        String output = templateEngine.process(template, context);
        assertThat(output).isEqualTo("<p>value</p>");
    }
}
