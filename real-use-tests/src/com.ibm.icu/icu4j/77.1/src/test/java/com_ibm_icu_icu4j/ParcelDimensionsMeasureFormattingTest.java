/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_ibm_icu_icu4j;

import com.ibm.icu.text.MeasureFormat;
import com.ibm.icu.util.Measure;
import com.ibm.icu.util.MeasureUnit;
import com.ibm.icu.util.ULocale;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises ICU4J's measurement-formatting subsystem to render parcel dimensions for localized shipping labels.
public class ParcelDimensionsMeasureFormattingTest {
    @Test
    public void formatsParcelDimensionsForDifferentLocales() {
        ParcelLabel label = new ParcelLabel();

        assertEquals("2 meters, 35 centimeters", label.dimensions(ULocale.US));
        assertEquals("2\u202Fm et 35\u202Fcm", label.shortDimensions(ULocale.FRANCE));
    }

    private static final class ParcelLabel {
        private String dimensions(ULocale locale) {
            MeasureFormat formatter = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.WIDE);
            return formatter.formatMeasures(
                    new Measure(2, MeasureUnit.METER),
                    new Measure(35, MeasureUnit.CENTIMETER));
        }

        private String shortDimensions(ULocale locale) {
            MeasureFormat formatter = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT);
            return formatter.formatMeasures(
                    new Measure(2, MeasureUnit.METER),
                    new Measure(35, MeasureUnit.CENTIMETER));
        }
    }
}
