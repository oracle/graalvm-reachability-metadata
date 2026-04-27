/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import javax.el.ELProcessor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AstFunctionTest {

    @Test
    void invokesMappedStaticFunctionDuringExpressionEvaluation()
            throws ClassNotFoundException, NoSuchMethodException {
        ELProcessor processor = new ELProcessor();
        processor.defineFunction(
                "lib",
                "repeatBySignature",
                FunctionLibrary.class.getName(),
                "java.lang.String repeat(java.lang.String,int)");

        Object value = processor.eval("lib:repeatBySignature('ha', 3)");

        assertThat(value).isEqualTo("hahaha");
    }

    public static final class FunctionLibrary {
        private FunctionLibrary() {
        }

        public static String repeat(String value, int count) {
            return value.repeat(count);
        }

        public static String repeat(String value) {
            return value;
        }
    }
}
