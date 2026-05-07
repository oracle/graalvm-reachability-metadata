/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core_model;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.builder.FlexibleAggregationStrategy;
import org.apache.camel.impl.engine.SimpleCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FlexibleAggregationStrategyTest {
    @Test
    void aggregateCreatesCollectionWithConfiguredCollectionTypeWhenGuardIsMissing() throws Exception {
        try (CamelContext camelContext = new SimpleCamelContext(false)) {
            Collection<String> originalBody = new ArrayList<>();
            originalBody.add("existing");

            Exchange oldExchange = new DefaultExchange(camelContext);
            oldExchange.getIn().setBody(originalBody);

            Exchange newExchange = new DefaultExchange(camelContext);
            newExchange.getIn().setBody("picked");

            FlexibleAggregationStrategy<String> strategy = new FlexibleAggregationStrategy<>(String.class)
                    .pick(new BodyExpression())
                    .accumulateInCollection(ArrayList.class);

            Exchange result = strategy.aggregate(oldExchange, newExchange);

            assertThat(result).isSameAs(oldExchange);
            assertThat(result.getIn().getBody()).isInstanceOf(ArrayList.class).isNotSameAs(originalBody);
            assertThat(result.getIn().getBody(ArrayList.class)).containsExactly("picked");
            assertThat(result.getProperty(Exchange.AGGREGATED_COLLECTION_GUARD, Boolean.class)).isFalse();
        }
    }

    private static final class BodyExpression implements Expression {
        @Override
        public <T> T evaluate(Exchange exchange, Class<T> type) {
            return type.cast(exchange.getIn().getBody());
        }
    }
}
