/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.thymeleaf;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.context.IdentifierSequences;
import org.thymeleaf.engine.TemplateData;
import org.thymeleaf.expression.Aggregates;
import org.thymeleaf.expression.Bools;
import org.thymeleaf.expression.Calendars;
import org.thymeleaf.expression.Dates;
import org.thymeleaf.expression.ExecutionInfo;
import org.thymeleaf.expression.IExpressionObjects;
import org.thymeleaf.expression.Ids;
import org.thymeleaf.expression.Maps;
import org.thymeleaf.expression.Numbers;
import org.thymeleaf.expression.Sets;
import org.thymeleaf.expression.Strings;
import org.thymeleaf.expression.Temporals;
import org.thymeleaf.expression.Uris;
import org.thymeleaf.inline.IInliner;
import org.thymeleaf.messageresolver.StandardMessageResolver;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.model.IProcessableElementTag;
import org.thymeleaf.templatemode.TemplateMode;

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
        Date date = new Date(81, 5, 15);
        String output = new Dates(Locale.US).format(date, "MMMM dd, YYYY");
        assertThat(output).startsWith("June 15, 1981");
    }

    @Test
    void renderCalendarsExpression() {
        Calendar calendar = Calendar.getInstance(Locale.US);
        calendar.clear();
        calendar.set(1981, Calendar.JUNE, 15);
        String output = new Calendars(Locale.US).format(calendar, "yyyy-MM-dd");
        assertThat(output).isEqualTo("1981-06-15");
    }

    @Test
    void renderNumbersExpression() {
        String output = new Numbers(Locale.US).formatInteger(10, 3);
        assertThat(output).isEqualTo("010");
    }

    @Test
    void renderStringsExpression() {
        String output = new Strings(Locale.US).toString(10);
        assertThat(output).isEqualTo("10");
    }

    @Test
    void renderObjectsExpression() {
        Integer output = new org.thymeleaf.expression.Objects().nullSafe(null, 10);
        assertThat(output).isEqualTo(10);
    }

    @Test
    void renderBoolsExpression() {
        Boolean output = new Bools().isFalse(true);
        assertThat(output).isFalse();
    }

    @Test
    void renderArraysExpression() {
        int output = new org.thymeleaf.expression.Arrays().length(new String[] {"one", "two"});
        assertThat(output).isEqualTo(2);
    }

    @Test
    void renderSetsExpression() {
        int output = new Sets().size(Collections.emptySet());
        assertThat(output).isEqualTo(0);
    }

    @Test
    void renderMapsExpression() {
        int output = new Maps().size(Collections.emptyMap());
        assertThat(output).isEqualTo(0);
    }

    @Test
    void renderAggregatesExpression() {
        BigDecimal output = new Aggregates().sum(new int[] {1, 2, 3});
        assertThat(output).isEqualByComparingTo("6");
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
        TestTemplateContext context = new TestTemplateContext("test-template", TemplateMode.HTML);
        ExecutionInfo executionInfo = new ExecutionInfo(context);
        assertThat(executionInfo.getTemplateName()).isEqualTo("test-template");
        assertThat(executionInfo.getProcessedTemplateName()).isEqualTo("test-template");
        assertThat(executionInfo.getTemplateMode()).isEqualTo(TemplateMode.HTML);
        assertThat(executionInfo.getTemplateNames()).containsExactly("test-template");
        assertThat(executionInfo.getNow()).isNotNull();
    }

    @Test
    void renderIdsExpression() {
        TestTemplateContext context = new TestTemplateContext("test-template", TemplateMode.HTML);
        Ids ids = new Ids(context);
        assertThat(ids.seq("property")).isEqualTo("property1");
        assertThat(ids.seq("property")).isEqualTo("property2");
        assertThat(ids.next("property")).isEqualTo("property3");
        assertThat(ids.prev("property")).isEqualTo("property2");
    }

    @Test
    void renderUrisExpression() {
        String output = new Uris().escapePath("/process?foo=bar");
        assertThat(output).isEqualTo("/process%3Ffoo=bar");
    }

    @Test
    void renderTemporalsExpression() {
        String output = new Temporals(Locale.US).format(LocalDateTime.of(1981, 6, 15, 0, 0), "dd/MM/yyyy");
        assertThat(output).isEqualTo("15/06/1981");
    }

    @Test
    void renderIteration() {
        TemplateEngine templateEngine = new TemplateEngine();
        Context context = new Context();
        context.setVariable("array", new String[] {"one", "two"});
        String template = "<ul><li th:each=\"value : ${array}\" th:text=\"${value}\">value</li></ul>";
        String output = templateEngine.process(template, context);
        assertThat(output).isEqualTo("<ul><li>one</li><li>two</li></ul>");
    }

    private static final class TestTemplateContext implements ITemplateContext {

        private final IdentifierSequences identifierSequences = new IdentifierSequences();
        private final Locale locale = Locale.US;
        private final TemplateData templateData;
        private final List<TemplateData> templateStack;

        private TestTemplateContext(final String templateName, final TemplateMode templateMode) {
            this.templateData = new TemplateData(templateName, null, null, templateMode, null);
            this.templateStack = Collections.singletonList(this.templateData);
        }

        @Override
        public TemplateData getTemplateData() {
            return this.templateData;
        }

        @Override
        public TemplateMode getTemplateMode() {
            return this.templateData.getTemplateMode();
        }

        @Override
        public List<TemplateData> getTemplateStack() {
            return this.templateStack;
        }

        @Override
        public List<IProcessableElementTag> getElementStack() {
            return Collections.emptyList();
        }

        @Override
        public Map<String, Object> getTemplateResolutionAttributes() {
            return Collections.emptyMap();
        }

        @Override
        public IModelFactory getModelFactory() {
            return null;
        }

        @Override
        public boolean hasSelectionTarget() {
            return false;
        }

        @Override
        public Object getSelectionTarget() {
            return null;
        }

        @Override
        public IInliner getInliner() {
            return null;
        }

        @Override
        public String getMessage(final Class<?> origin, final String key, final Object[] messageParameters,
                final boolean useAbsentMessageRepresentation) {
            return null;
        }

        @Override
        public String buildLink(final String base, final Map<String, Object> parameters) {
            return base;
        }

        @Override
        public IdentifierSequences getIdentifierSequences() {
            return this.identifierSequences;
        }

        @Override
        public org.thymeleaf.IEngineConfiguration getConfiguration() {
            return null;
        }

        @Override
        public IExpressionObjects getExpressionObjects() {
            return null;
        }

        @Override
        public Locale getLocale() {
            return this.locale;
        }

        @Override
        public boolean containsVariable(final String name) {
            return false;
        }

        @Override
        public Set<String> getVariableNames() {
            return Collections.emptySet();
        }

        @Override
        public Object getVariable(final String name) {
            return null;
        }
    }
}
