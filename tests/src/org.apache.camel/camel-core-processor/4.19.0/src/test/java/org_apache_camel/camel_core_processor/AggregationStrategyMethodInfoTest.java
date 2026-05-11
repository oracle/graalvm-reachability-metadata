/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core_processor;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AggregationStrategyMethodInfoTest {
    @Test
    void invokesAggregationMethodAndStoresResultOnOldExchange() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            AggregationStrategyBeanAdapter adapter = new AggregationStrategyBeanAdapter(new ConcatenatingAggregationBean(), "merge");
            Exchange oldExchange = exchangeWithBody(context, "old");
            Exchange newExchange = exchangeWithBody(context, "new");

            try {
                adapter.start();

                Exchange result = adapter.aggregate(oldExchange, newExchange);

                assertThat(result).isSameAs(oldExchange);
                assertThat(result.getException()).isNull();
                assertThat(result.getIn().getBody(String.class)).isEqualTo("old:new");
            } finally {
                adapter.stop();
            }
        }
    }

    private static Exchange exchangeWithBody(DefaultCamelContext context, String body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);
        return exchange;
    }

    public static final class ConcatenatingAggregationBean {
        public String merge(String oldBody, String newBody) {
            return oldBody + ":" + newBody;
        }
    }
}
