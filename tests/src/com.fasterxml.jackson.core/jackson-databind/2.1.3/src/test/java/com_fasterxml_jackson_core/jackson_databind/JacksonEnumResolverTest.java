/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import java.lang.reflect.Method;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.util.EnumResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonEnumResolverTest {

    @Test
    void enumResolverInvokesAccessorMethodsToResolveValues() throws Exception {
        Method accessor = ResolvedEnum.class.getDeclaredMethod("jsonValue");
        EnumResolver<?> resolver = EnumResolver.constructUsingMethod(ResolvedEnum.class, accessor);
        assertThat(resolver.findEnum("resolved")).isEqualTo(ResolvedEnum.VALUE);
    }

    enum ResolvedEnum {
        VALUE;

        @JsonValue
        public String jsonValue() {
            return "resolved";
        }
    }
}
