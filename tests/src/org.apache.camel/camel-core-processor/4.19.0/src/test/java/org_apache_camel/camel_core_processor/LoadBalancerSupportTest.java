/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core_processor;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.processor.loadbalancer.LoadBalancerSupport;
import org.apache.camel.support.AsyncProcessorSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LoadBalancerSupportTest {
    @Test
    void removesExistingProcessorFromLoadBalancer() {
        LoadBalancerSupport loadBalancer = new TestLoadBalancer();
        TestAsyncProcessor firstProcessor = new TestAsyncProcessor();
        TestAsyncProcessor secondProcessor = new TestAsyncProcessor();

        assertThat(loadBalancer.hasNext()).isFalse();
        assertThat(loadBalancer.next()).isNull();

        loadBalancer.addProcessor(firstProcessor);
        loadBalancer.addProcessor(secondProcessor);

        assertThat(loadBalancer.hasNext()).isTrue();
        assertThat(loadBalancer.getProcessors()).containsExactly(firstProcessor, secondProcessor);
        assertThat(loadBalancer.next()).containsExactly(firstProcessor, secondProcessor);

        loadBalancer.removeProcessor(firstProcessor);

        assertThat(loadBalancer.hasNext()).isTrue();
        assertThat(loadBalancer.getProcessors()).containsExactly(secondProcessor);
        assertThat(loadBalancer.next()).containsExactly((Processor) secondProcessor);

        loadBalancer.removeProcessor(secondProcessor);

        assertThat(loadBalancer.hasNext()).isFalse();
        assertThat(loadBalancer.getProcessors()).isEmpty();
        assertThat(loadBalancer.next()).isNull();
    }

    private static final class TestLoadBalancer extends LoadBalancerSupport {
        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            callback.done(true);
            return true;
        }
    }

    private static final class TestAsyncProcessor extends AsyncProcessorSupport {
        @Override
        public boolean process(Exchange exchange, AsyncCallback callback) {
            callback.done(true);
            return true;
        }
    }
}
