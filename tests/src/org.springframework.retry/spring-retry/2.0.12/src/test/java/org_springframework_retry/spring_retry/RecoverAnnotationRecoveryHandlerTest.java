/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_retry.spring_retry;

import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.retry.annotation.AnnotationAwareRetryOperationsInterceptor;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;

import static org.assertj.core.api.Assertions.assertThat;

public class RecoverAnnotationRecoveryHandlerTest {

    @Test
    void invokesRecoveryThroughTheServiceProxyAfterRetriesAreExhausted() {
        RecoveringService target = new RecoveringService();
        AtomicInteger interceptedRecoveries = new AtomicInteger();
        ProxyFactory factory = new ProxyFactory(target);
        factory.addAdvice((MethodInterceptor) invocation -> {
            if (invocation.getMethod().getName().equals("recover")) {
                interceptedRecoveries.incrementAndGet();
            }
            return invocation.proceed();
        });
        factory.addAdvisor(new DefaultPointcutAdvisor(new AnnotationAwareRetryOperationsInterceptor()));
        RecoverableLookup service = (RecoverableLookup) factory.getProxy();

        assertThat(service.lookup("invoice-42")).isEqualTo("cached:invoice-42");

        assertThat(target.attempts).isEqualTo(2);
        assertThat(target.recoveries).isEqualTo(1);
        assertThat(target.recoveredFailure).isSameAs(target.lastFailure);
        assertThat(target.recoveredRequest).isEqualTo("invoice-42");
        assertThat(interceptedRecoveries.get()).isEqualTo(1);
    }

    @Test
    void invokesRecoveryOnTheTargetWhenTheProxyDoesNotExposeIt() {
        RecoveringService target = new RecoveringService();
        ProxyFactory factory = new ProxyFactory(target);
        factory.setInterfaces(Lookup.class);
        factory.addAdvisor(new DefaultPointcutAdvisor(new AnnotationAwareRetryOperationsInterceptor()));
        Lookup service = (Lookup) factory.getProxy();

        assertThat(service.lookup("invoice-73")).isEqualTo("cached:invoice-73");

        assertThat(target.attempts).isEqualTo(2);
        assertThat(target.recoveries).isEqualTo(1);
        assertThat(target.recoveredFailure).isSameAs(target.lastFailure);
        assertThat(target.recoveredRequest).isEqualTo("invoice-73");
    }

    public interface Lookup {
        String lookup(String request);
    }

    public interface RecoverableLookup extends Lookup {
        String recover(IllegalArgumentException failure, String request);
    }

    public static class RecoveringService implements RecoverableLookup {
        private int attempts;
        private int recoveries;
        private IllegalArgumentException lastFailure;
        private IllegalArgumentException recoveredFailure;
        private String recoveredRequest;

        @Override
        @Retryable(retryFor = IllegalArgumentException.class, maxAttempts = 2,
                recover = "recover", backoff = @Backoff(delay = 1))
        public String lookup(String request) {
            attempts++;
            lastFailure = new IllegalArgumentException("Unavailable: " + request);
            throw lastFailure;
        }

        @Override
        @Recover
        public String recover(IllegalArgumentException failure, String request) {
            recoveries++;
            recoveredFailure = failure;
            recoveredRequest = request;
            return "cached:" + request;
        }
    }
}
