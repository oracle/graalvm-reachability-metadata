/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.swing.XMLTableColumnDefinition;
import org.junit.jupiter.api.Test;

public class XMLTableColumnDefinitionTest {
    @Test
    void resolvesColumnClassesForSupportedValueTypes() {
        assertThat(columnClassFor(XMLTableColumnDefinition.STRING_TYPE))
                .isEqualTo(String.class);
        assertThat(columnClassFor(XMLTableColumnDefinition.NUMBER_TYPE))
                .isEqualTo(Number.class);
        assertThat(columnClassFor(XMLTableColumnDefinition.NODE_TYPE))
                .isEqualTo(Node.class);
        assertThat(columnClassFor(XMLTableColumnDefinition.OBJECT_TYPE))
                .isEqualTo(Object.class);
    }

    @Test
    void evaluatesConfiguredXPathUsingColumnType() throws Exception {
        Document document = DocumentHelper.parseText("""
                <order>
                    <id>A-42</id>
                    <total>19.5</total>
                    <customer name="Ada"/>
                </order>
                """);
        Element row = document.getRootElement();

        XMLTableColumnDefinition stringColumn = new XMLTableColumnDefinition(
                "id", "id", XMLTableColumnDefinition.STRING_TYPE);
        XMLTableColumnDefinition numberColumn = new XMLTableColumnDefinition(
                "total", "number(total)", XMLTableColumnDefinition.NUMBER_TYPE);
        XMLTableColumnDefinition nodeColumn = new XMLTableColumnDefinition(
                "customer", "customer", XMLTableColumnDefinition.NODE_TYPE);
        XMLTableColumnDefinition objectColumn = new XMLTableColumnDefinition(
                "attributes", "customer/@name",
                XMLTableColumnDefinition.OBJECT_TYPE);

        assertThat(stringColumn.getValue(row)).isEqualTo("A-42");
        assertThat(((Number) numberColumn.getValue(row)).doubleValue())
                .isEqualTo(19.5d);
        assertThat((Node) nodeColumn.getValue(row)).extracting(Node::getName)
                .isEqualTo("customer");
        assertThat((List<?>) objectColumn.getValue(row))
                .extracting(item -> ((Node) item).getText())
                .containsExactly("Ada");
    }

    private static Class<?> columnClassFor(int type) {
        XMLTableColumnDefinition column = new XMLTableColumnDefinition();
        column.setType(type);

        return column.getColumnClass();
    }
}
