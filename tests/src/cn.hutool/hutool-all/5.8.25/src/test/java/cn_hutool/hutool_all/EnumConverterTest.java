/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.convert.impl.EnumConverter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EnumConverterTest {

    @Test
    void convertsUsingCustomStaticFactoryMethod() {
        EnumConverter converter = new EnumConverter(ExternalState.class);

        Object converted = converter.convert(new ExternalStateCode("ready"), null);

        assertThat(converted).isEqualTo(ExternalState.READY);
    }

    private enum ExternalState {
        READY("ready"),
        CLOSED("closed");

        private final String code;

        ExternalState(String code) {
            this.code = code;
        }

        public static ExternalState fromExternalCode(ExternalStateCode code) {
            for (ExternalState state : values()) {
                if (state.code.equals(code.value())) {
                    return state;
                }
            }
            throw new IllegalArgumentException(code.value());
        }
    }

    private static final class ExternalStateCode {
        private final String value;

        private ExternalStateCode(String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }
    }
}
