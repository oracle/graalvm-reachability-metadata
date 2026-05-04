/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_threeten.threetenbp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.threeten.bp.chrono.Chronology;
import org.threeten.bp.chrono.IsoChronology;
import org.threeten.bp.chrono.JapaneseChronology;
import org.threeten.bp.chrono.ThaiBuddhistChronology;

public class ChronologyTest {
    @Test
    void ofLocaleUsesUnicodeCalendarExtension() {
        Locale thaiBuddhistLocale = Locale.forLanguageTag("th-TH-u-ca-buddhist");

        Chronology chronology = Chronology.ofLocale(thaiBuddhistLocale);

        assertThat(chronology).isSameAs(ThaiBuddhistChronology.INSTANCE);
        assertThat(chronology.getId()).isEqualTo("ThaiBuddhist");
        assertThat(chronology.getCalendarType()).isEqualTo("buddhist");
    }

    @Test
    void ofLocaleResolvesIsoAndJapaneseCalendars() {
        Locale japaneseLocale = Locale.forLanguageTag("ja-JP-u-ca-japanese");

        Chronology isoChronology = Chronology.ofLocale(Locale.US);
        Chronology japaneseChronology = Chronology.ofLocale(japaneseLocale);

        assertThat(isoChronology).isSameAs(IsoChronology.INSTANCE);
        assertThat(japaneseChronology).isSameAs(JapaneseChronology.INSTANCE);
        assertThat(japaneseChronology.getCalendarType()).isEqualTo("japanese");
    }
}
