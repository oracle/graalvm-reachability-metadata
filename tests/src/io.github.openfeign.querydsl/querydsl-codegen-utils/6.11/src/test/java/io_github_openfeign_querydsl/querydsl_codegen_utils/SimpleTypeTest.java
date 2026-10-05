/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_openfeign_querydsl.querydsl_codegen_utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.querydsl.codegen.utils.model.SimpleType;
import org.junit.jupiter.api.Test;

public class SimpleTypeTest {

    @Test
    void resolvesArrayTypeToItsJavaClass() {
        SimpleType type = new SimpleType("java.lang.String[]", "java.lang", "String[]");

        assertThat(type.getComponentType().getJavaClass()).isEqualTo(String.class);
        assertThat(type.getJavaClass()).isEqualTo(String[].class);
    }
}
