/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_jupiter.junit_jupiter_params;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.shadow.com.univocity.parsers.conversions.EnumConversion;

public class EnumConversionTest {

    @Test
    void convertsEnumsUsingCustomFields() {
        EnumConversion<FieldValue> conversion = new EnumConversion<>(FieldValue.class, "code");

        assertThat(conversion.execute("second-code")).isEqualTo(FieldValue.SECOND);
        assertThat(conversion.revert(FieldValue.FIRST)).isEqualTo("first-code");
    }

    @Test
    void convertsEnumsUsingCustomInstanceMethods() {
        EnumConversion<MethodValue> conversion = new EnumConversion<>(MethodValue.class, "code");

        assertThat(conversion.execute("second-method")).isEqualTo(MethodValue.SECOND);
        assertThat(conversion.revert(MethodValue.FIRST)).isEqualTo("first-method");
    }

    @Test
    void convertsEnumsUsingCustomFactoryMethods() {
        EnumConversion<FactoryValue> conversion = new EnumConversion<>(FactoryValue.class, "fromCode");

        assertThat(conversion.execute("second-factory")).isEqualTo(FactoryValue.SECOND);
    }

    private enum FieldValue {
        FIRST("first-code"),
        SECOND("second-code");

        private final String code;

        FieldValue(String code) {
            this.code = code;
        }
    }

    private enum MethodValue {
        FIRST("first-method"),
        SECOND("second-method");

        private final String code;

        MethodValue(String code) {
            this.code = code;
        }

        private String code() {
            return code;
        }
    }

    private enum FactoryValue {
        FIRST("first-factory"),
        SECOND("second-factory");

        private final String code;

        FactoryValue(String code) {
            this.code = code;
        }

        private static FactoryValue fromCode(String code) {
            for (FactoryValue value : values()) {
                if (value.code.equals(code)) {
                    return value;
                }
            }
            return null;
        }
    }
}
