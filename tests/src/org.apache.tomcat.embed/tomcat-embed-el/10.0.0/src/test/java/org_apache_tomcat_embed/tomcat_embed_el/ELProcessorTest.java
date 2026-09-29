/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_el;

import java.lang.reflect.Method;

import javax.el.ELProcessor;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ELProcessorTest {

    @Test
    void definesFunctionFromBinaryClassName() throws ClassNotFoundException, NoSuchMethodException {
        ELProcessor processor = new ELProcessor();

        processor.defineFunction("lib", "joinByName", FunctionLibrary.class.getName(), "join");

        Method mappedMethod = processor.getELManager().getELContext().getFunctionMapper()
                .resolveFunction("lib", "joinByName");
        assertThat(mappedMethod).isNotNull();
        assertThat(mappedMethod.getDeclaringClass()).isEqualTo(FunctionLibrary.class);
        assertThat(mappedMethod.getName()).isEqualTo("join");
        assertThat(mappedMethod.getParameterTypes()).isEmpty();
    }

    @Test
    void definesFunctionFromSignatureWhenOverloadsExist() throws ClassNotFoundException, NoSuchMethodException {
        ELProcessor processor = new ELProcessor();

        processor.defineFunction(
                "lib",
                "repeatBySignature",
                FunctionLibrary.class.getName(),
                "java.lang.String repeat(java.lang.String,int)");

        Method mappedMethod = processor.getELManager().getELContext().getFunctionMapper()
                .resolveFunction("lib", "repeatBySignature");
        assertThat(mappedMethod).isNotNull();
        assertThat(mappedMethod.getDeclaringClass()).isEqualTo(FunctionLibrary.class);
        assertThat(mappedMethod.getName()).isEqualTo("repeat");
        assertThat(mappedMethod.getParameterTypes()).containsExactly(String.class, int.class);
    }

    public static final class FunctionLibrary {
        private FunctionLibrary() {
        }

        public static String join() {
            return "joined";
        }

        public static String repeat(String value, int count) {
            return value.repeat(count);
        }

        public static String repeat(String value) {
            return value;
        }
    }
}
