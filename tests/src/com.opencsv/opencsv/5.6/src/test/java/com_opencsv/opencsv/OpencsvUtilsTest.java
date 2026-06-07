/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.opencsv.bean.util.OpencsvUtils;
import com.opencsv.exceptions.CsvBadConverterException;
import java.util.IllegalFormatConversionException;
import java.util.Locale;
import java.util.regex.PatternSyntaxException;
import org.junit.jupiter.api.Test;

public class OpencsvUtilsTest {
    @Test
    void reportsInvalidRegexWithLocalizedMessage() {
        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> OpencsvUtils.compilePattern("(", 0, OpencsvUtilsTest.class, Locale.US))
                .satisfies(exception -> {
                    assertThat(exception.getConverterClass()).isEqualTo(OpencsvUtilsTest.class);
                    assertThat(exception).hasCauseInstanceOf(PatternSyntaxException.class);
                });
    }

    @Test
    void reportsRegexWithoutCaptureGroupWithLocalizedMessage() {
        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> OpencsvUtils.compilePatternAtLeastOneGroup(
                        "[A-Z]+", 0, OpencsvUtilsTest.class, Locale.US))
                .satisfies(exception -> assertThat(exception.getConverterClass()).isEqualTo(OpencsvUtilsTest.class));
    }

    @Test
    void reportsInvalidSingleParameterFormatStringWithLocalizedMessage() {
        assertThatExceptionOfType(CsvBadConverterException.class)
                .isThrownBy(() -> OpencsvUtils.verifyFormatString("%d", OpencsvUtilsTest.class, Locale.US))
                .satisfies(exception -> {
                    assertThat(exception.getConverterClass()).isEqualTo(OpencsvUtilsTest.class);
                    assertThat(exception).hasCauseInstanceOf(IllegalFormatConversionException.class);
                });
    }
}
