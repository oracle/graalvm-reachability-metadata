/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package dom4j.dom4j;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static Class<?> columnClassFor(int type) {
        XMLTableColumnDefinition column = new XMLTableColumnDefinition();
        column.setType(type);

        return column.getColumnClass();
    }
}
