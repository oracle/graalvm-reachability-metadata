/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.aop.ThrowsAdvice;
import org.springframework.aop.framework.ProxyFactory;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ThrowsAdviceInterceptorTest {
    public interface Work {
        void execute();
    }

    public static class RejectedWork implements Work {
        private final IllegalArgumentException failure = new IllegalArgumentException("invalid request");

        public void execute() {
            throw failure;
        }
    }

    public static class Recorder implements ThrowsAdvice {
        private IllegalArgumentException observed;

        public void afterThrowing(IllegalArgumentException failure) {
            observed = failure;
        }
    }

    @Test
    void callsTypedExceptionAdviceAndPreservesOriginalFailure() {
        RejectedWork target = new RejectedWork();
        Recorder recorder = new Recorder();
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice(recorder);
        Work proxy = (Work) factory.getProxy();
        assertSame(target.failure, assertThrows(IllegalArgumentException.class, proxy::execute));
        assertSame(target.failure, recorder.observed);
    }
}
