/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.i18n.client.PluralRule;
import com.google.gwt.i18n.client.impl.plurals.DefaultRule_en;
import com.google.gwt.i18n.server.AbstractParameter;
import com.google.gwt.i18n.server.GwtLocaleFactoryImpl;
import com.google.gwt.i18n.shared.GwtLocale;
import com.google.gwt.i18n.shared.GwtLocaleFactory;

import org.junit.jupiter.api.Test;

public class AbstractParameterTest {
    private static final GwtLocaleFactory LOCALE_FACTORY = new GwtLocaleFactoryImpl();

    @Test
    void createsPlainPluralRuleWithoutLocalizedLookup() {
        GwtLocale locale = LOCALE_FACTORY.fromString("en");

        PluralRule pluralRule = AbstractParameter.getLocalizedPluralRule(PlainPluralRule.class, locale);

        assertThat(pluralRule).isInstanceOf(PlainPluralRule.class);
        assertThat(pluralRule.select(1)).isEqualTo(0);
        assertThat(pluralRule.pluralForms())
                .extracting(PluralRule.PluralForm::getName)
                .containsExactly("other");
    }

    @Test
    void createsLocalizedDefaultPluralRuleForRequestedLocale() {
        GwtLocale locale = LOCALE_FACTORY.fromString("en");

        PluralRule pluralRule = AbstractParameter.getLocalizedPluralRule(PluralRule.class, locale);

        assertThat(pluralRule).isInstanceOf(DefaultRule_en.class);
        assertThat(pluralRule.select(1)).isEqualTo(1);
        assertThat(pluralRule.select(2)).isEqualTo(0);
        assertThat(pluralRule.pluralForms())
                .extracting(PluralRule.PluralForm::getName)
                .containsExactly("other", "one");
    }

    public static final class PlainPluralRule implements PluralRule {
        public PlainPluralRule() {
        }

        @Override
        public PluralForm[] pluralForms() {
            return new PluralForm[] {
                    new PluralForm("other", "Plain plural form"),
            };
        }

        @Override
        public int select(int n) {
            return 0;
        }
    }
}
