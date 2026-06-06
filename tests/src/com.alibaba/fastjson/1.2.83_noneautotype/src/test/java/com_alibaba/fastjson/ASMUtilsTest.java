/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.fastjson;

import static org.assertj.core.api.Assertions.assertThat;

import com.alibaba.fastjson.util.ASMUtils;

import java.lang.reflect.Type;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ASMUtilsTest {
    @Test
    void getMethodTypeReturnsGenericReturnTypeForPublicNoArgumentMethod() {
        Type valuesType = ASMUtils.getMethodType(GenericValueProvider.class, "getValues");
        Type numberType = ASMUtils.getMethodType(GenericValueProvider.class, "getNumber");

        assertThat(valuesType).hasToString("java.util.List<java.lang.String>");
        assertThat(numberType).isEqualTo(int.class);
    }

    public interface GenericValueProvider {
        List<String> getValues();

        int getNumber();
    }
}
