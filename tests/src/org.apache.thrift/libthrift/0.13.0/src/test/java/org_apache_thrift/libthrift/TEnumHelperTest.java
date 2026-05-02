/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_thrift.libthrift;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.thrift.TEnum;
import org.apache.thrift.TEnumHelper;
import org.junit.jupiter.api.Test;

public class TEnumHelperTest {
    @Test
    void getByValueFindsThriftEnumConstantThroughGeneratedLookupMethod() {
        TEnum enumValue = TEnumHelper.getByValue(ExampleThriftEnum.class, 2);

        assertThat(enumValue).isSameAs(ExampleThriftEnum.SECOND);
        assertThat(enumValue.getValue()).isEqualTo(2);
    }

    @Test
    void getByValueReturnsNullWhenGeneratedLookupMethodHasNoMatch() {
        TEnum enumValue = TEnumHelper.getByValue(ExampleThriftEnum.class, 99);

        assertThat(enumValue).isNull();
    }

    public enum ExampleThriftEnum implements TEnum {
        FIRST(1),
        SECOND(2);

        private final int value;

        ExampleThriftEnum(int value) {
            this.value = value;
        }

        public static ExampleThriftEnum findByValue(int value) {
            switch (value) {
                case 1:
                    return FIRST;
                case 2:
                    return SECOND;
                default:
                    return null;
            }
        }

        @Override
        public int getValue() {
            return value;
        }
    }
}
