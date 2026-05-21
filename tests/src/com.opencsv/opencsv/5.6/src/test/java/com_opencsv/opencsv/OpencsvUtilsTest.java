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

import java.util.IllegalFormatException;
import java.util.Locale;
import java.util.regex.PatternSyntaxException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class OpencsvUtilsTest {
    @Test
    void reportsInvalidRegularExpressionUsingLocalizedMessage() {
        assertThatThrownBy(() -> OpencsvUtils.compilePattern("[", 0, OpencsvUtilsTest.class, Locale.US))
                .isInstanceOf(CsvBadConverterException.class)
                .hasRootCauseInstanceOf(PatternSyntaxException.class)
                .hasMessageContaining("regular expression is invalid")
                .hasMessageContaining("[");
    }

    @Test
    void reportsRegularExpressionWithoutCaptureGroupUsingLocalizedMessage() {
        assertThatThrownBy(() -> OpencsvUtils.compilePatternAtLeastOneGroup(
                "[A-Z]+", 0, OpencsvUtilsTest.class, Locale.US))
                .isInstanceOf(CsvBadConverterException.class)
                .hasMessageContaining("at least one capture group")
                .hasMessageContaining("[A-Z]+");
    }

    @Test
    void reportsInvalidSingleParameterFormatStringUsingLocalizedMessage() {
        assertThatThrownBy(() -> OpencsvUtils.verifyFormatString("%d", OpencsvUtilsTest.class, Locale.US))
                .isInstanceOf(CsvBadConverterException.class)
                .hasRootCauseInstanceOf(IllegalFormatException.class)
                .hasMessageContaining("format string is not valid")
                .hasMessageContaining("%d");
    }
}
