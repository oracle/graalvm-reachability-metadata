/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack.handlebars_helpers;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.ext.AssignHelper;
import com.github.jknack.handlebars.helper.ext.IncludeHelper;
import com.github.jknack.handlebars.helper.ext.JodaHelper;
import com.github.jknack.handlebars.helper.ext.NumberHelper;
import com.github.jknack.handlebars.io.StringTemplateSource;
import com.github.jknack.handlebars.io.TemplateLoader;
import com.github.jknack.handlebars.io.TemplateSource;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Handlebars_helpersTest {
    @Test
    void numberHelpersRenderEvenOddStripesAndIgnoreNonNumbers() throws IOException {
        final Handlebars handlebars = new Handlebars();
        NumberHelper.register(handlebars);

        final Template template = handlebars.compileInline("""
                even={{isEven even "EVEN"}};
                odd={{isOdd odd "ODD"}};
                default-even={{isEven zero}};
                default-odd={{isOdd one}};
                stripes={{stripes zero "left" "right"}}/{{stripes one "left" "right"}};
                non-number=[{{isEven text "unexpected"}}]
                """);

        final Map<String, Object> model = new HashMap<>();
        model.put("even", 4);
        model.put("odd", 7L);
        model.put("zero", 0);
        model.put("one", 1);
        model.put("text", "not a number");

        assertThat(template.apply(model)).isEqualTo("""
                even=EVEN;
                odd=ODD;
                default-even=even;
                default-odd=odd;
                stripes=left/right;
                non-number=[]
                """);
    }

    @Test
    void assignHelperStoresTrimmedBlockOutputAsDataForLaterUse() throws IOException {
        final Handlebars handlebars = new Handlebars();
        handlebars.registerHelper(AssignHelper.NAME, AssignHelper.INSTANCE);

        final Template template = handlebars.compileInline(
                "{{#assign \"greeting\"}} Hello {{name}} {{/assign}}Greeting:{{greeting}}");

        final Map<String, Object> model = new HashMap<>();
        model.put("name", "Ada");

        assertThat(template.apply(model)).isEqualTo("Greeting:Hello Ada");
    }

    @Test
    void includeHelperLoadsTemplateWithHashDataAndReturnsSafeHtml() throws IOException {
        final MapBackedTemplateLoader loader = new MapBackedTemplateLoader();
        loader.put("profile", "<article>{{title}}: {{name}}</article>");

        final Handlebars handlebars = new Handlebars(loader);
        handlebars.registerHelper(IncludeHelper.NAME, IncludeHelper.INSTANCE);

        final Template template = handlebars.compileInline("""
                {{include "profile" title="User" name=displayName}}|{{title}}|{{name}}
                """);

        final Map<String, Object> model = new HashMap<>();
        model.put("displayName", "Grace Hopper");

        assertThat(template.apply(model)).isEqualTo("""
                <article>User: Grace Hopper</article>|User|Grace Hopper
                """);
    }

    @Test
    void includeHelperEscapesIncludedTemplateDataBeforeReturningSafeHtml() throws IOException {
        final MapBackedTemplateLoader loader = new MapBackedTemplateLoader();
        loader.put("bio", "<strong>{{displayName}}</strong>");

        final Handlebars handlebars = new Handlebars(loader);
        handlebars.registerHelper(IncludeHelper.NAME, IncludeHelper.INSTANCE);

        final Template template = handlebars.compileInline("{{include \"bio\"}}");

        final Map<String, Object> model = new HashMap<>();
        model.put("displayName", "<Ada & Grace>");

        assertThat(template.apply(model)).isEqualTo("<strong>&lt;Ada &amp; Grace&gt;</strong>");
    }

    @Test
    void includeHelperResolvesTemplateLoaderPrefixAndSuffixWithCallerContext() throws IOException {
        final MapBackedTemplateLoader loader = new MapBackedTemplateLoader();
        loader.setPrefix("/templates/");
        loader.setSuffix(".hbs");
        loader.put("/templates/summary.hbs", "{{firstName}} {{lastName}} is {{role}}");

        final Handlebars handlebars = new Handlebars(loader);
        handlebars.registerHelper(IncludeHelper.NAME, IncludeHelper.INSTANCE);

        final Template template = handlebars.compileInline("{{include \"summary\"}}");

        final Map<String, Object> model = new HashMap<>();
        model.put("firstName", "Ada");
        model.put("lastName", "Lovelace");
        model.put("role", "mathematician");

        assertThat(template.apply(model)).isEqualTo("Ada Lovelace is mathematician");
    }

    @Test
    void jodaHelpersFormatReadableInstantsWithPatternStyleAndIsoOutput() throws IOException {
        final Locale originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
        try {
            final Handlebars handlebars = new Handlebars();
            handlebars.registerHelper(JodaHelper.jodaPattern.name(), JodaHelper.jodaPattern);
            handlebars.registerHelper(JodaHelper.jodaStyle.name(), JodaHelper.jodaStyle);
            handlebars.registerHelper(JodaHelper.jodaISO.name(), JodaHelper.jodaISO);

            final Template template = handlebars.compileInline("""
                    {{jodaPattern when "yyyy-MM-dd HH:mm:ss.SSS Z"}}|
                    {{jodaPattern when}}|
                    {{jodaStyle when "S-"}}|
                    {{jodaISO when}}|
                    {{jodaISO when "unused" true}}
                    """);

            final Map<String, Object> model = new HashMap<>();
            model.put("when", new DateTime(2023, 4, 5, 6, 7, 8, 9, DateTimeZone.UTC));

            assertThat(template.apply(model)).isEqualTo("""
                    2023-04-05 06:07:08.009 +0000|
                    4 5 2023, 6:7:8 UTC|
                    4/5/23|
                    2023-04-05T06:07:08Z|
                    2023-04-05T06:07:08.009Z
                    """);
        } finally {
            Locale.setDefault(originalLocale);
        }
    }

    private static final class MapBackedTemplateLoader implements TemplateLoader {
        private final Map<String, String> templates = new HashMap<>();
        private Charset charset = StandardCharsets.UTF_8;
        private String prefix = "";
        private String suffix = "";

        void put(final String name, final String source) {
            templates.put(name, source);
        }

        @Override
        public TemplateSource sourceAt(final String location) throws IOException {
            final String resolvedLocation = resolve(location);
            final String source = templates.containsKey(location)
                    ? templates.get(location)
                    : templates.get(resolvedLocation);
            if (source == null) {
                throw new IOException("Missing test template: " + location);
            }
            return new StringTemplateSource(resolvedLocation, source);
        }

        @Override
        public String resolve(final String location) {
            return prefix + location + suffix;
        }

        @Override
        public String getPrefix() {
            return prefix;
        }

        @Override
        public String getSuffix() {
            return suffix;
        }

        @Override
        public void setPrefix(final String prefix) {
            this.prefix = prefix;
        }

        @Override
        public void setSuffix(final String suffix) {
            this.suffix = suffix;
        }

        @Override
        public void setCharset(final Charset charset) {
            this.charset = charset;
        }

        @Override
        public Charset getCharset() {
            return charset;
        }
    }
}
