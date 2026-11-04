/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack_handlebars;

import com.github.jknack.handlebars.Decorator;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.Template;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Handlebars' decorator subsystem to add an application-wide report label.
public class ReportDecoratorTest {
    @Test
    @Timeout(60)
    public void addsReportLabelThroughTemplateDecorator() throws IOException {
        Handlebars handlebars = new Handlebars();
        handlebars.registerHelper("report", new ReportHelper());
        handlebars.registerDecorator("reportLabel", new ReportLabelDecorator());
        Template template = handlebars.compileInline(
                "<article>{{#report}}{{#*reportLabel \"Operations\"}}"
                        + "<h1>{{title}}</h1><p data-label=\"{{@reportLabel}}\">{{summary}}</p>"
                        + "{{/reportLabel}}{{/report}}</article>");

        String rendered = template.apply(Map.of(
                "title", "Weekly status",
                "summary", "All services are healthy"));

        assertEquals(
                "<article><h1>Weekly status</h1>"
                        + "<p data-label=\"Operations\">All services are healthy</p></article>",
                rendered);
    }

    private static final class ReportHelper implements Helper<Object> {
        @Override
        public Object apply(Object context, Options options) throws IOException {
            return options.data("reportBody");
        }
    }

    private static final class ReportLabelDecorator implements Decorator {
        @Override
        public void apply(Template function, Options options) throws IOException {
            options.data("reportLabel", options.param(0));
            options.data("reportBody", options.fn());
        }
    }
}
