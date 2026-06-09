/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.processor.aggregate.AggregationStrategyBeanAdapter;
import org.junit.jupiter.api.Test;

public class AggregationStrategyBeanAdapterTest {
    @Test
    void startsNamedBeanMethodAndAggregatesExchangeBodies() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        AggregationStrategyBeanAdapter adapter = new AggregationStrategyBeanAdapter(
                new NamedAggregationBean(), "merge");
        adapter.setCamelContext(context);

        try {
            adapter.start();
            Exchange oldExchange = exchangeWithBody(context, "old");
            Exchange newExchange = exchangeWithBody(context, "new");

            Exchange result = adapter.aggregate(oldExchange, newExchange);

            assertThat(result).isSameAs(oldExchange);
            assertThat(result.getIn().getBody()).isEqualTo("old+new");
            assertThat(result.getException()).isNull();
        } finally {
            adapter.stop();
            context.stop();
        }
    }

    @Test
    void startsImplicitSingleMethodTypeAndCreatesBeanInstance() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        AggregationStrategyBeanAdapter adapter = new AggregationStrategyBeanAdapter(
                SingleMethodAggregationBean.class);
        adapter.setCamelContext(context);

        try {
            adapter.start();
            Exchange oldExchange = exchangeWithBody(context, "left");
            Exchange newExchange = exchangeWithBody(context, "right");

            Exchange result = adapter.aggregate(oldExchange, newExchange);

            assertThat(result).isSameAs(oldExchange);
            assertThat(result.getIn().getBody()).isEqualTo("left|right");
            assertThat(result.getException()).isNull();
        } finally {
            adapter.stop();
            context.stop();
        }
    }

    private static Exchange exchangeWithBody(DefaultCamelContext context, String body) {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(body);
        return exchange;
    }

    public static class NamedAggregationBean {
        public String merge(String oldBody, String newBody) {
            return oldBody + "+" + newBody;
        }
    }

    public static class SingleMethodAggregationBean {
        public String combine(String oldBody, String newBody) {
            return oldBody + "|" + newBody;
        }
    }
}
