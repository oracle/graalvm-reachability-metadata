/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_htrace.htrace_core;

import org.apache.htrace.Sampler;
import org.apache.htrace.wrappers.TraceProxy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TraceProxyAnonymous1Test {
    @Test
    void invokesTargetMethodWhenSamplerDeclinesTracing() {
        GreetingService target = new RecordingGreetingService();
        GreetingService traced = TraceProxy.trace(target, Sampler.NEVER);

        String greeting = traced.greet("Ada");

        assertThat(greeting).isEqualTo("Hello, Ada!");
        assertThat(target.invocationCount()).isEqualTo(1);
    }

    @Test
    void invokesTargetMethodInsideTraceScopeWhenSamplerAcceptsTracing() {
        GreetingService target = new RecordingGreetingService();
        GreetingService traced = TraceProxy.trace(target, Sampler.ALWAYS);

        String greeting = traced.greet("Grace");

        assertThat(greeting).isEqualTo("Hello, Grace!");
        assertThat(target.invocationCount()).isEqualTo(1);
    }

    public interface GreetingService {
        String greet(String name);

        int invocationCount();
    }

    private static final class RecordingGreetingService implements GreetingService {
        private int invocationCount;

        @Override
        public String greet(String name) {
            invocationCount++;
            return "Hello, " + name + "!";
        }

        @Override
        public int invocationCount() {
            return invocationCount;
        }
    }
}
