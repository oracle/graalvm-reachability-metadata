/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_threeten.threetenbp;

import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.threeten.bp.chrono.Chronology;
import org.threeten.bp.chrono.IsoChronology;
import org.threeten.bp.chrono.JapaneseChronology;
import org.threeten.bp.chrono.MinguoChronology;
import org.threeten.bp.chrono.ThaiBuddhistChronology;

public class ChronologyTest {
    @Test
    void resolvesChronologyFromUnicodeCalendarLocaleExtension() {
        assertSame(ThaiBuddhistChronology.INSTANCE, Chronology.ofLocale(Locale.forLanguageTag("th-TH-u-ca-buddhist")));
        assertSame(JapaneseChronology.INSTANCE, Chronology.ofLocale(Locale.forLanguageTag("ja-JP-u-ca-japanese")));
        assertSame(MinguoChronology.INSTANCE, Chronology.ofLocale(Locale.forLanguageTag("zh-TW-u-ca-roc")));
    }

    @Test
    void resolvesIsoChronologyWhenLocaleHasNoCalendarExtension() {
        assertSame(IsoChronology.INSTANCE, Chronology.ofLocale(Locale.US));
    }
}
