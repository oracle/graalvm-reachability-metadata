/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_dom4j.dom4j;

import com.sun.msv.datatype.xsd.DatatypeFactory;
import com.sun.msv.datatype.xsd.XSDatatype;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.dom4j.datatype.DatatypeElement;
import org.dom4j.datatype.DatatypeElementFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises dom4j's schema datatype subsystem to validate and consume order values in an application.
public class DatatypeOrderValidationTest {
    @Test
    @Timeout(60)
    public void validatesOrderValuesAndExposesTypedData() throws Exception {
        Namespace orders = Namespace.get("o", "urn:orders");
        DatatypeElementFactory factory = new DatatypeElementFactory(
                QName.get("order", orders));
        QName orderName = factory.createQName("order", orders);
        QName idName = factory.createQName("id");
        QName quantityName = factory.createQName("quantity", orders);
        QName totalName = factory.createQName("total", orders);

        XSDatatype stringType = DatatypeFactory.getTypeByName("string");
        XSDatatype integerType = DatatypeFactory.getTypeByName("integer");
        XSDatatype decimalType = DatatypeFactory.getTypeByName("decimal");
        factory.setAttributeXSDatatype(idName, stringType);
        factory.setChildElementXSDatatype(quantityName, integerType);
        factory.setChildElementXSDatatype(totalName, decimalType);

        Element order = factory.createElement(orderName);
        order.addAttribute(idName, "ORD-2048");
        Element quantity = order.addElement(quantityName);
        quantity.addText("3");
        Element total = order.addElement(totalName);
        total.addText("42.50");
        Document document = factory.createDocument(order);

        DatatypeElement typedQuantity = assertInstanceOf(DatatypeElement.class, quantity);
        DatatypeElement typedTotal = assertInstanceOf(DatatypeElement.class, total);
        assertEquals("ORD-2048", document.getRootElement().attributeValue(idName));
        assertEquals(new BigInteger("3"), typedQuantity.getData());
        assertEquals(0, new BigDecimal("42.50").compareTo((BigDecimal) typedTotal.getData()));
        assertEquals("3", typedQuantity.getText());
        assertEquals("42.50", typedTotal.getText());

        assertThrows(IllegalArgumentException.class,
                () -> order.addElement(quantityName).addText("three"));
    }
}
