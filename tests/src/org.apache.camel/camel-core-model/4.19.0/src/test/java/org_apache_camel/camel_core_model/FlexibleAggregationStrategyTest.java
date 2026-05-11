/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core_model;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.FlexibleAggregationStrategy;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FlexibleAggregationStrategyTest {
    @Test
    void aggregateIntoUnguardedCollectionInstantiatesConfiguredCollectionType() throws Exception {
        try (DefaultCamelContext camelContext = new DefaultCamelContext()) {
            Exchange oldExchange = new DefaultExchange(camelContext);
            oldExchange.getIn().setBody(new ArrayList<>(List.of("stale")));
            Exchange newExchange = new DefaultExchange(camelContext);
            newExchange.getIn().setBody("first");

            FlexibleAggregationStrategy<String> strategy = new FlexibleAggregationStrategy<>(String.class)
                    .accumulateInCollection(ArrayList.class);

            Exchange result = strategy.aggregate(oldExchange, newExchange);
            Object aggregatedBody = result.getIn().getBody();

            assertThat(aggregatedBody).isInstanceOf(ArrayList.class);
            assertThat(aggregatedBody).isEqualTo(List.of("first"));
            assertThat(result.getProperty(Exchange.AGGREGATED_COLLECTION_GUARD)).isEqualTo(Boolean.FALSE);
        }
    }
}
