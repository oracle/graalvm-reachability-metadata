/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_querydsl.codegen_utils;

import com.querydsl.codegen.utils.AbstractEvaluatorFactory;
import com.querydsl.codegen.utils.CodegenException;
import com.querydsl.codegen.utils.ECJEvaluatorFactory;
import com.querydsl.codegen.utils.model.ClassType;
import com.querydsl.codegen.utils.model.Type;
import com.querydsl.codegen.utils.model.TypeCategory;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AbstractEvaluatorFactoryTest {

    @Test
    void propagatesCompilationDiagnosticsForAnIncompatibleProjection() {
        AbstractEvaluatorFactory factory = new ECJEvaluatorFactory(getClass().getClassLoader());
        ClassType projection = new ClassType(TypeCategory.SIMPLE, String.class);
        Type parameterType = new ClassType(TypeCategory.SIMPLE, Integer.class);

        assertThatThrownBy(() -> factory.createEvaluator(
                "return value;", projection, new String[]{"value"},
                new Type[]{parameterType}, new Class<?>[]{Integer.class}, Collections.emptyMap()))
                .isInstanceOf(CodegenException.class)
                .hasMessageContaining("return value;")
                .hasMessageContaining("cannot convert from Integer to String");
    }
}
