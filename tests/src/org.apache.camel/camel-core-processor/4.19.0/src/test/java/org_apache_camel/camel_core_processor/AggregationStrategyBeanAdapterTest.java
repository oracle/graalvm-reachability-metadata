/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core_processor;

import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AggregationStrategyBeanAdapterTest {
    @Test
    void startsWithExplicitMethodName() {
        AggregationStrategyBeanAdapter adapter = new AggregationStrategyBeanAdapter(new NamedAggregationBean(), "merge");

        startAndStop(adapter);
    }

    @Test
    void startsWithImplicitSingleBeanMethod() {
        AggregationStrategyBeanAdapter adapter = new AggregationStrategyBeanAdapter(new SingleMethodAggregationBean());

        startAndStop(adapter);
    }

    private static void startAndStop(AggregationStrategyBeanAdapter adapter) {
        try {
            adapter.start();
            assertThat(adapter.isStarted()).isTrue();
        } finally {
            adapter.stop();
        }
    }

    public static final class NamedAggregationBean {
        public String merge(String oldBody, String newBody) {
            return oldBody + newBody;
        }
    }

    public static final class SingleMethodAggregationBean {
        public String aggregate(String oldBody, String newBody) {
            return oldBody + newBody;
        }
    }
}
