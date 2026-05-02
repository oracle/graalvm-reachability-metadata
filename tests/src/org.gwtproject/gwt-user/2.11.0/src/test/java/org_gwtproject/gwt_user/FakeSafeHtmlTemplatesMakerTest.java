/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.junit.FakeSafeHtmlTemplatesMaker;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;

import org.junit.jupiter.api.Test;

public class FakeSafeHtmlTemplatesMakerTest {
    @Test
    void createsSafeHtmlTemplatesProxyReturningMethodNamesAndArguments() {
        SampleTemplates templates = FakeSafeHtmlTemplatesMaker.create(SampleTemplates.class);

        SafeHtml safeMessage = SafeHtmlUtils.fromString("safe message");
        SafeHtml message = templates.messageWithLink(
                safeMessage, "https://example.test", "link", "primary");

        assertThat(message.asString())
                .isEqualTo("messageWithLink[safe message, https://example.test, link, primary]");
        assertThat(templates.empty().asString()).isEqualTo("empty");
    }

    public interface SampleTemplates extends SafeHtmlTemplates {
        @Template("{0}<a href=\"{1}\" class=\"{3}\">{2}</a>")
        SafeHtml messageWithLink(SafeHtml message, String url, String linkText, String style);

        @Template("empty")
        SafeHtml empty();
    }
}
