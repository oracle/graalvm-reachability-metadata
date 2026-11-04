/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_jknack_handlebars;

import com.github.jknack.handlebars.Context;
import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.helper.I18nHelper;
import com.github.jknack.handlebars.helper.I18nSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises Handlebars' internationalization-helper subsystem for a localized catalog header.
public class LocalizedCatalogTest {
    @Test
    @Timeout(60)
    public void rendersCatalogHeaderInRequestedLocale() throws IOException {
        I18nHelper.i18n.setDefaultLocale(Locale.ENGLISH);
        I18nHelper.i18n.setSource(new CatalogMessages());

        Handlebars handlebars = new Handlebars();
        handlebars.registerHelper("t", I18nHelper.i18n);
        Template template = handlebars.compileInline(
                "<header><h1>{{t \"greeting\" customer locale=locale bundle=\"catalog\"}}</h1></header>");

        String rendered = template.apply(Context.newBuilder(Map.of(
                "customer", "Mara & Co.",
                "locale", "de",
                "bundle", "catalog")).build());

        assertEquals(
                "<header><h1>Willkommen, Mara &amp; Co.!</h1></header>",
                rendered);
    }

    private static final class CatalogMessages implements I18nSource {
        private final Map<Locale, Map<String, String>> messages = Map.of(
                Locale.ENGLISH, Map.of("greeting", "Welcome, {0}!"),
                Locale.GERMAN, Map.of("greeting", "Willkommen, {0}!"));

        @Override
        public String[] keys(String bundle, Locale locale) {
            return messages.getOrDefault(locale, Map.of()).keySet().toArray(String[]::new);
        }

        @Override
        public String message(String key, Locale locale, Object... parameters) {
            String pattern = messages.getOrDefault(locale, Map.of()).get(key);
            if (pattern == null) {
                throw new IllegalArgumentException("Unknown message: " + key);
            }
            return new MessageFormat(pattern, locale).format(parameters);
        }
    }
}
