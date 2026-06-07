/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_expression;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import static org.assertj.core.api.Assertions.assertThat;

public class IndexerInnerCollectionIndexingValueRefTest {
    private final SpelExpressionParser parser = new SpelExpressionParser(new SpelParserConfiguration(true, true));

    @Test
    void growsTypedCollectionByConstructingMissingElements() {
        Order order = new Order();
        Expression expression = this.parser.parseExpression("items[2]");

        LineItem item = expression.getValue(order, LineItem.class);

        assertThat(item)
                .isNotNull();
        assertThat(order.getItems())
                .hasSize(3)
                .allSatisfy(lineItem -> assertThat(lineItem.getDescription()).isEqualTo("new item"));
    }

    public static class Order {
        private final List<LineItem> items = new ArrayList<>();

        public List<LineItem> getItems() {
            return this.items;
        }
    }

    public static class LineItem {
        private final String description;

        public LineItem() {
            this.description = "new item";
        }

        public String getDescription() {
            return this.description;
        }
    }
}
