/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.util.MethodInvoker;
import org.springframework.util.StringUtils;

public class MethodInvokerTest {

    @Test
    void preparesAndInvokesPublicStaticMethodWithExactArguments() throws Exception {
        MethodInvoker invoker = new MethodInvoker();
        invoker.setTargetClass(StringUtils.class);
        invoker.setTargetMethod("capitalize");
        invoker.setArguments("spring");

        invoker.prepare();
        Object result = invoker.invoke();

        assertThat(invoker.isPrepared()).isTrue();
        assertThat(result).isEqualTo("Spring");
    }

    @Test
    void preparesAndInvokesPublicInstanceMethodWithExactArguments() throws Exception {
        SampleFormatter formatter = new SampleFormatter("Spring");
        MethodInvoker invoker = new MethodInvoker();
        invoker.setTargetObject(formatter);
        invoker.setTargetMethod("format");
        invoker.setArguments("Core");

        invoker.prepare();
        Object result = invoker.invoke();

        assertThat(invoker.isPrepared()).isTrue();
        assertThat(result).isEqualTo("Spring Core");
    }

    public static class SampleFormatter {

        private final String prefix;

        public SampleFormatter(String prefix) {
            this.prefix = prefix;
        }

        public String format(String value) {
            return this.prefix + " " + value;
        }
    }
}
