/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.thymeleaf;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.messageresolver.StandardMessageResolver;

import static org.assertj.core.api.Assertions.assertThat;


public class ThymeleafTest {

    @Test
    void renderSimpleTemplate() {
        TemplateEngine templateEngine = new TemplateEngine();
        Context context = new Context();
        context.setVariable("key", "value");
        String template = "<p th:text=\"${key}\"></p>";
        String output = templateEngine.process(template, context);
        assertThat(output).isEqualTo("<p>value</p>");
    }

    @SuppressWarnings("deprecation")
    @Test
    void renderDatesExpression() {
        TemplateEngine templateEngine = new TemplateEngine();
        Context context = new Context();
        Date date = new Date(81, 5, 15);
        context.setVariable("date", date);
        String template = "<p th:text=\"${#dates.format(date)}\"></p>";
        String output = templateEngine.process(template, context);
        assertThat(output).startsWith("<p>June 15, 1981");
    }

    @Test
    void renderCalendarsExpression() {
        TemplateEngine templateEngine = new TemplateEngine();
        Context context = new Context();
        Calendar calendar = Calendar.getInstance();
        context.setVariable("calendar", calendar);
        String template = "<p th:text=\"${#calendars.format(calendar)}\"></p>";
        templateEngine.process(template, context);
    }

    @Test
    void renderNumbersExpression() {
        TemplateEngine templateEngine = new TemplateEngine();
        Context context = new Context();
        context.setVariable("number", 10);
        String template = "<p th:text=\"${#numbers.formatInteger(number,3)}\"></p>";
        String output = templateEngine.process(template, context);
        assertThat(output).isEqualTo("<p>010</p>");
    }

    @Test
    void renderStringsExpression() {
        TemplateEngine templateEngine = new TemplateEngine();
        Context context = new Context();
        context.setVariable("str", 10);
        String template = "<p th:text=\"${#strings.toString(str)}\"></p>";
        String output = templateEngine.process(template, context);
        assertThat(output).isEqualTo("<p>10</p>");
    }

    @Test
    void renderObjectsExpression() {
        TemplateEngine templateEngine = new TemplateEngine();
        Context context = new Context();
        context.setVariable("obj", null);
        context.setVariable("str", 10);
        String template = "<p th:text=\"${#objects.nullSafe(obj, str)}\"></p>";
        String output = templateEngine.process(template, context);
        assertThat(output).isEqualTo("<p>10</p>");
    }

    @Test
    void renderBoolsExpression() {
        TemplateEngine templateEngine = new TemplateEngine();
        Context context = new Context();
        context.setVariable("cond", true);
        String template = "<p th:text=\"${#bools.isFalse(cond)}\"></p>";
        String output = templateEngine.process(template, context);
        assertThat(output).isEqualTo("<p>false</p>");
    }

    @Test
    void renderArraysExpression() {
        TemplateEngine templateEngine = new TemplateEngine();
        Context context = new Context();
        context.setVariable("array", new String[] {"one", "two"});
        String template = "<p th:text=\"${#arrays.length(array)}\"></p>";
        String output = templateEngine.process(template, context);
        assertThat(output).isEqualTo("<p>2</p>");
    }

    @Test
    void renderSetsExpression() {
        TemplateEngine templateEngine = new TemplateEngine();
        Context context = new Context();
        context.setVariable("set", Collections.emptySet());
        String template = "<p th:text=\"${#sets.size(set)}\"></p>";
        String output = templateEngine.process(template, context);
        assertThat(output).isEqualTo("<p>0</p>");
    }

    @Test
    void renderMapsExpression() {
        TemplateEngine templateEngine = new TemplateEngine();
        Context context = new Context();
        context.setVariable("map", Collections.emptyMap());
        String template = "<p th:text=\"${#maps.size(map)}\"></p>";
        String output = templateEngine.process(template, context);
        assertThat(output).isEqualTo("<p>0</p>");
    }

    @Test
    void renderAggregatesExpression() {
        TemplateEngine templateEngine = new TemplateEngine();
        Context context = new Context();
        context.setVariable("array", new int[] {1, 2, 3});
        String template = "<p th:text=\"${#aggregates.sum(array)}\"></p>";
        String output = templateEngine.process(template, context);
        assertThat(output).isEqualTo("<p>6</p>");
    }

    @Test
    void renderMessagesExpression() {
        TemplateEngine templateEngine = new TemplateEngine();
        StandardMessageResolver messageResolver = new StandardMessageResolver();
        messageResolver.addDefaultMessage("message", "Hello");
        templateEngine.addMessageResolver(messageResolver);
        Context context = new Context();
        String template = "<p th:text=\"#{message}\"></p>";
        String output = templateEngine.process(template, context);
        assertThat(output).isEqualTo("<p>Hello</p>");
    }

    @Test
    void renderExecInfoExpression() {
        TemplateEngine templateEngine = new TemplateEngine();
        Context context = new Context();
        context.setVariable("array", new int[] {1, 2, 3});
        String template = "<p th:text=\"${#execInfo.getNow()}\"></p>";
        templateEngine.process(template, context);
    }

    @Test
    void renderIdsExpression() {
        TemplateEngine templateEngine = new TemplateEngine();
        Context context = new Context();
        context.setVariable("list", Arrays.asList("a", "b", "c"));
        String template = "<ul><li th:each=\"property: ${list}\" th:id=\"${#ids.seq('property')}\" th:text=\"${property}\"></li></ul>";
        String output = templateEngine.process(template, context);
        assertThat(output).isEqualTo("<ul><li id=\"property1\">a</li><li id=\"property2\">b</li><li id=\"property3\">c</li></ul>");
    }

    @Test
    void renderUrisExpression() {
        TemplateEngine templateEngine = new TemplateEngine();
        Context context = new Context();
        context.setVariable("uri", "/process?foo=bar");
        String template = "<p th:text=\"${#uris.escapePath(uri)}\"></p>";
        String output = templateEngine.process(template, context);
        assertThat(output).isEqualTo("<p>/process%3Ffoo=bar</p>");
    }

    @Test
    void renderTemporalsExpression() {
        TemplateEngine templateEngine = new TemplateEngine();
        Context context = new Context();
        context.setVariable("localDateTime", LocalDateTime.of(1981, 6, 15, 0, 0));
        String template = "<p th:text=\"${#temporals.format(localDateTime, 'dd/MM/yyyy')}\"></p>";
        String output = templateEngine.process(template, context);
        assertThat(output).startsWith("<p>15/06/1981</p>");
    }


}
