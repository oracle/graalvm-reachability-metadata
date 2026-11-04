/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_dom4j.dom4j;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.swing.XMLTableDefinition;
import org.dom4j.swing.XMLTableModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises dom4j's Swing table-model subsystem to display inventory data in a desktop application.
public class XmlTableInventoryModelTest {
    @Test
    @Timeout(60)
    public void exposesCatalogRowsThroughConfiguredTableModel() throws Exception {
        Document catalog = DocumentHelper.parseText("""
                <catalog warehouse="lyon">
                  <product sku="TEA-17">
                    <name>Green tea</name>
                    <stock>12</stock>
                  </product>
                  <product sku="COF-04">
                    <name>Espresso beans</name>
                    <stock>8</stock>
                  </product>
                </catalog>
                """);
        Document tableDefinition = DocumentHelper.parseText("""
                <table select="/catalog/product">
                  <column select="@sku" type="string">SKU</column>
                  <column select="name" type="string">Product</column>
                  <column select="stock" type="number">Units</column>
                </table>
                """);

        XMLTableDefinition definition = XMLTableDefinition.load(tableDefinition);
        XMLTableModel model = new XMLTableModel(definition, catalog);

        assertEquals(List.of("SKU", "Product", "Units"), List.of(
                model.getColumnName(0), model.getColumnName(1), model.getColumnName(2)));
        assertEquals(2, model.getRowCount());
        assertEquals(String.class, model.getColumnClass(0));
        assertEquals(Number.class, model.getColumnClass(2));
        assertEquals(List.of("TEA-17", "Green tea", 12.0), List.of(
                model.getValueAt(0, 0), model.getValueAt(0, 1), model.getValueAt(0, 2)));
        assertEquals(List.of("COF-04", "Espresso beans", 8.0), List.of(
                model.getValueAt(1, 0), model.getValueAt(1, 1), model.getValueAt(1, 2)));
    }
}
