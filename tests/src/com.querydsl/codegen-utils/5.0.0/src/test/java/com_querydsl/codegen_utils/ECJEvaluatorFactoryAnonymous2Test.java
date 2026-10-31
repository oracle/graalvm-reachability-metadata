/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.codegen_utils;

import com.querydsl.codegen.utils.CodegenException;
import com.querydsl.codegen.utils.ECJEvaluatorFactory;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ECJEvaluatorFactoryAnonymous2Test {

    @Test
    void reportsAnUndefinedMethodOnAResolvedParameterType() {
        ECJEvaluatorFactory factory = new ECJEvaluatorFactory(getClass().getClassLoader());

        assertThatThrownBy(() -> factory.createEvaluator(
                "return text.missingCodegenOperation();", String.class,
                new String[]{"text"}, new Class<?>[]{String.class}, Collections.emptyMap()))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("missingCodegenOperation")
                .hasMessageContaining("undefined")
                .hasMessageContaining("String");
    }
}
