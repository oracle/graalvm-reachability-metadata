/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_opencsv.opencsv;

import com.opencsv.bean.util.OpencsvUtils;
import com.opencsv.exceptions.CsvBadConverterException;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OpencsvUtilsTest {

    private static final Locale ERROR_LOCALE = Locale.US;

    @Test
    void compilePatternReturnsCompiledPatternForValidRegex() {
        Pattern pattern = OpencsvUtils.compilePattern(
                "[a-z]+", 0, OpencsvUtilsTest.class, ERROR_LOCALE);

        assertThat(pattern.matcher("opencsv").matches()).isTrue();
    }

    @Test
    void compilePatternReportsInvalidRegex() {
        assertThatThrownBy(() -> OpencsvUtils.compilePattern(
                "[", 0, OpencsvUtilsTest.class, ERROR_LOCALE))
                .isInstanceOf(CsvBadConverterException.class)
                .satisfies(exception -> assertThat(exception.getMessage()).isNotBlank())
                .hasCauseInstanceOf(RuntimeException.class);
    }

    @Test
    void compilePatternAtLeastOneGroupReportsRegexWithoutCaptureGroup() {
        assertThatThrownBy(() -> OpencsvUtils.compilePatternAtLeastOneGroup(
                "[a-z]+", 0, OpencsvUtilsTest.class, ERROR_LOCALE))
                .isInstanceOf(CsvBadConverterException.class)
                .satisfies(exception -> assertThat(exception.getMessage()).isNotBlank());
    }

    @Test
    void verifyFormatStringAcceptsSingleStringParameterFormat() {
        OpencsvUtils.verifyFormatString("value=%s", OpencsvUtilsTest.class, ERROR_LOCALE);
    }

    @Test
    void verifyFormatStringReportsFormatThatCannotUseStringParameter() {
        assertThatThrownBy(() -> OpencsvUtils.verifyFormatString(
                "value=%d", OpencsvUtilsTest.class, ERROR_LOCALE))
                .isInstanceOf(CsvBadConverterException.class)
                .satisfies(exception -> assertThat(exception.getMessage()).isNotBlank())
                .hasCauseInstanceOf(RuntimeException.class);
    }
}
