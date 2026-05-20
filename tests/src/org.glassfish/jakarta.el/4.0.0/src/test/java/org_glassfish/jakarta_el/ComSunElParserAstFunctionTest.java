/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish.jakarta_el;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sun.el.lang.ELSupport;

import jakarta.el.ELProcessor;

public class ComSunElParserAstFunctionTest {

    @Test
    void invokesMappedStaticFunctionWhenEvaluatingExpressionFunction() throws Exception {
        ELProcessor processor = new ELProcessor();
        processor.defineFunction("support", "asString", ELSupport.class.getName(), "coerceToString");

        Object result = processor.eval("support:asString(42)");

        assertThat(result).isEqualTo("42");
    }
}
