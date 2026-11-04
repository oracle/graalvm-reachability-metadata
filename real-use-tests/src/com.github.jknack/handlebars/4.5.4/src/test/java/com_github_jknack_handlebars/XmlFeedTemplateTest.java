/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack_handlebars;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.EscapingStrategy;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Handlebars' escaping-strategy subsystem to render an XML feed entry.
public class XmlFeedTemplateTest {
    @Test
    @Timeout(60)
    public void rendersXmlFeedWithEscapedConsumerData() throws IOException {
        Handlebars handlebars = new Handlebars().with(EscapingStrategy.XML);
        Template template = handlebars.compileInline(
                "<entry id=\"{{id}}\"><title>{{title}}</title>"
                        + "<summary>{{summary}}</summary></entry>");
        Map<String, String> entry = Map.of(
                "id", "item-7&8",
                "title", "Tools < Essentials",
                "summary", "Use \"care\" & check O'Reilly");

        String rendered = template.apply(Context.newBuilder(entry).build());

        assertEquals(
                "<entry id=\"item-7&amp;8\"><title>Tools &lt; Essentials</title>"
                        + "<summary>Use &quot;care&quot; &amp; check O&apos;Reilly</summary></entry>",
                rendered);
    }
}
