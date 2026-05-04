/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjrt;

import org.aspectj.internal.lang.reflect.StringToType;
import org.aspectj.lang.reflect.AjType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class StringToTypeTest {
    @Test
    void convertsParameterizedTypeNamesToTypes() throws Exception {
        Type type = StringToType.stringToType("java.util.List<java.lang.String>", StringToTypeTest.class);

        assertThat(type).isInstanceOf(ParameterizedType.class);
        ParameterizedType parameterizedType = (ParameterizedType) type;
        assertThat(parameterizedType.getRawType()).isEqualTo(List.class);
        assertThat(parameterizedType.getOwnerType()).isNull();
        assertThat(parameterizedType.getActualTypeArguments()).hasSize(1);
        assertThat(parameterizedType.getActualTypeArguments()[0])
                .isInstanceOf(AjType.class)
                .extracting(argument -> ((AjType<?>) argument).getJavaClass())
                .isEqualTo(String.class);
    }
}
