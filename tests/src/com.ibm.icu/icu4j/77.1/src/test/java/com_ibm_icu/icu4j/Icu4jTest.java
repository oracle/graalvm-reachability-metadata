/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ibm_icu.icu4j;

import static org.assertj.core.api.Assertions.assertThat;

import com.ibm.icu.number.NumberFormatter;
import com.ibm.icu.text.BreakIterator;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.CurrencyMetaInfo;
import com.ibm.icu.text.IDNA;
import com.ibm.icu.text.LocaleDisplayNames;
import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.text.Normalizer2;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.RuleBasedNumberFormat;
import com.ibm.icu.text.TimeZoneNames;
import com.ibm.icu.text.Transliterator;
import com.ibm.icu.util.Currency;
import com.ibm.icu.util.Holiday;
import com.ibm.icu.util.MeasureUnit;
import com.ibm.icu.util.ULocale;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Icu4jTest {
    @Test
    void formatsAndParsesLocalizedCurrency() throws ParseException {
        Currency currency = Currency.getInstance(ULocale.US);
        NumberFormat format = NumberFormat.getCurrencyInstance(ULocale.US);
        format.setCurrency(currency);

        String formatted = format.format(1234.5);
        Number parsed = format.parse(formatted);

        assertThat(currency.getCurrencyCode()).isEqualTo("USD");
        assertThat(formatted).isEqualTo("$1,234.50");
        assertThat(parsed.doubleValue()).isEqualTo(1234.5);
        assertThat(CurrencyMetaInfo.getInstance().currencies(CurrencyMetaInfo.CurrencyFilter.onRegion("US")))
                .contains("USD");
    }

    @Test
    void appliesLocaleSpecificCollation() {
        Collator collator = Collator.getInstance(new ULocale("sv_SE"));

        assertThat(collator.compare("z", "\u00e5")).isNegative();
        assertThat(collator.compare("\u00e5", "\u00e4")).isNegative();
        assertThat(collator.compare("\u00e4", "\u00f6")).isNegative();
    }

    @Test
    void segmentsTextIntoUnicodeWords() {
        String text = "Hello, \u4e16\u754c! ICU.";
        BreakIterator iterator = BreakIterator.getWordInstance(ULocale.ENGLISH);
        iterator.setText(text);
        List<String> words = new ArrayList<>();

        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String segment = text.substring(start, end);
            if (Character.isLetter(segment.codePointAt(0))) {
                words.add(segment);
            }
        }

        assertThat(words).containsExactly("Hello", "\u4e16\u754c", "ICU");
    }

    @Test
    void transliteratesAcrossScriptsUsingRegisteredTransforms() {
        Transliterator transliterator = Transliterator.getInstance("Any-Latin; Latin-ASCII");

        assertThat(transliterator.transliterate("\u041c\u043e\u0441\u043a\u0432\u0430")).isEqualTo("Moskva");
        assertThat(transliterator.transliterate("\u039a\u03b1\u03bb\u03b7\u03bc\u03ad\u03c1\u03b1"))
                .isEqualTo("Kalemera");
    }

    @Test
    void spellsOutAndLenientlyParsesNumbers() throws ParseException {
        RuleBasedNumberFormat format =
                new RuleBasedNumberFormat(ULocale.ENGLISH, RuleBasedNumberFormat.SPELLOUT);
        format.setLenientParseMode(true);

        assertThat(format.format(42)).isEqualTo("forty-two");
        assertThat(format.parse("forty two").longValue()).isEqualTo(42L);
    }

    @Test
    void loadsLocalizedDisplayNames() {
        LocaleDisplayNames names = LocaleDisplayNames.getInstance(ULocale.FRENCH);
        String displayName = names.localeDisplayName(new ULocale("de_DE"));

        assertThat(displayName).startsWith("allemand").contains("Allemagne");
    }

    @Test
    void loadsLocalizedTimeZoneNames() {
        TimeZoneNames names = TimeZoneNames.getInstance(ULocale.US);

        assertThat(names.getDisplayName("America/Los_Angeles", TimeZoneNames.NameType.LONG_STANDARD, 1577934245000L))
                .isEqualTo("Pacific Standard Time");
        assertThat(names.getExemplarLocationName("America/Los_Angeles")).isEqualTo("Los Angeles");
    }

    @Test
    void formatsMeasurementUnitsWithLocaleData() {
        String formatted = NumberFormatter.withLocale(ULocale.US)
                .unit(MeasureUnit.KILOMETER)
                .unitWidth(NumberFormatter.UnitWidth.FULL_NAME)
                .format(12)
                .toString();

        assertThat(formatted).isEqualTo("12 kilometers");
    }

    @Test
    void resolvesHolidayResourceBundles() {
        Holiday[] holidays = Holiday.getHolidays(ULocale.US);
        List<String> names = new ArrayList<>();
        for (Holiday holiday : holidays) {
            names.add(holiday.getDisplayName(ULocale.US));
        }

        assertThat(names).contains("New Year's Day", "Independence Day", "Christmas");
    }

    @Test
    void formatsPluralMessages() {
        MessageFormat format = new MessageFormat(
                "{count, plural, =0{No files} one{# file} other{# files}}", ULocale.ENGLISH);
        Map<String, Object> arguments = new HashMap<>();

        arguments.put("count", 0);
        assertThat(format.format(arguments)).isEqualTo("No files");
        arguments.put("count", 1);
        assertThat(format.format(arguments)).isEqualTo("1 file");
        arguments.put("count", 12);
        assertThat(format.format(arguments)).isEqualTo("12 files");
    }

    @Test
    void normalizesUnicodeAndProcessesInternationalDomainNames() {
        Normalizer2 normalizer = Normalizer2.getNFCInstance();
        IDNA idna = IDNA.getUTS46Instance(IDNA.DEFAULT);
        IDNA.Info info = new IDNA.Info();
        StringBuilder asciiName = new StringBuilder();

        assertThat(normalizer.normalize("Cafe\u0301")).isEqualTo("Caf\u00e9");
        idna.nameToASCII("b\u00fccher.example", asciiName, info);
        assertThat(info.hasErrors()).isFalse();
        assertThat(asciiName.toString()).isEqualTo("xn--bcher-kva.example");
    }
}
