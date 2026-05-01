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
    void convertsUsingCustomStaticEnumFactory() {
        EnumConverter converter = new EnumConverter(WorkflowState.class);

        Object converted = converter.convert(Integer.valueOf(30), null);

        assertThat(converted).isEqualTo(WorkflowState.PUBLISHED);
    }

    public enum WorkflowState {
        DRAFT(10),
        PUBLISHED(30);

        private final int code;

        WorkflowState(int code) {
            this.code = code;
        }

        public static WorkflowState fromCode(Integer code) {
            for (WorkflowState state : values()) {
                if (state.code == code.intValue()) {
                    return state;
                }
            }
            return null;
        }
    }
}
