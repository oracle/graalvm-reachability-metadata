/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf_spring6;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(value = 3, unit = TimeUnit.MINUTES)
public class SPELVariableExpressionEvaluatorTest {

    @Test
    void joinsStringsThroughSpringExpressionHelper() {
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        Context context = new Context(Locale.ROOT);
        context.setVariable("values", List.of("alpha", "beta"));

        String output = templateEngine.process("<p th:text=\"${#strings.listJoin(values, ', ')}\"></p>", context);

        assertThat(output).isEqualTo("<p>alpha, beta</p>");
    }

    @Test
    void formatsTemporalThroughSpringExpressionHelper() {
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        Context context = new Context(Locale.ROOT);
        context.setVariable("date", LocalDate.of(2024, 2, 29));

        String output = templateEngine.process("<p th:text=\"${#temporals.format(date, 'dd/MM/yyyy')}\"></p>", context);

        assertThat(output).isEqualTo("<p>29/02/2024</p>");
    }

    @Test
    void rendersPageNumbersThroughSpringExpressionHelper() {
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        Context context = new Context(Locale.ROOT);
        context.setVariable("lastPage", 3);
        String template = "<span th:each=\"page : ${#numbers.sequence(1, lastPage)}\" th:text=\"${page}\"></span>";

        String output = templateEngine.process(template, context);

        assertThat(output).isEqualTo("<span>1</span><span>2</span><span>3</span>");
    }
}
