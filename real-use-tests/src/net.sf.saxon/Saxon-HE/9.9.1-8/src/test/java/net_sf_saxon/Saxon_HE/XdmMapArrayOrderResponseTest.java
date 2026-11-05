/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.s9api.XdmArray;
import net.sf.saxon.s9api.XdmAtomicValue;
import net.sf.saxon.s9api.XdmMap;
import net.sf.saxon.s9api.XdmValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises Saxon's XDM map and array subsystem to assemble a fulfillment response.
 *
 * An order service uses Saxon's XDM structures for a JSON-like response before handing it to
 * another application component. The service reads the response through the same public API a
 * consumer uses, preserving product order and nested product tags.
 */
public class XdmMapArrayOrderResponseTest {

    @Test
    @Timeout(60)
    public void readsNestedOrderResponseStructures() {
        XdmMap response = buildResponse();

        assertEquals("ORD-2048", response.get("orderId").itemAt(0).getStringValue());
        assertEquals("ready", response.get("status").itemAt(0).getStringValue());

        XdmArray products = (XdmArray) response.get("products").itemAt(0);
        assertEquals(2, products.arrayLength());
        assertEquals("NOTEBOOK-42", product(products, 0).get("sku").itemAt(0).getStringValue());
        assertEquals("MUG-9", product(products, 1).get("sku").itemAt(0).getStringValue());
        assertEquals(List.of("office", "recycled"), strings(product(products, 0).get("tags")));
        assertEquals(List.of("kitchen"), strings(product(products, 1).get("tags")));
    }

    private static XdmMap buildResponse() {
        XdmMap notebook = product("NOTEBOOK-42", "office", "recycled");
        XdmMap mug = product("MUG-9", "kitchen");
        XdmArray products = new XdmArray().addMember(notebook).addMember(mug);
        return new XdmMap()
                .put(new XdmAtomicValue("orderId"), new XdmAtomicValue("ORD-2048"))
                .put(new XdmAtomicValue("status"), new XdmAtomicValue("ready"))
                .put(new XdmAtomicValue("products"), products);
    }

    private static XdmMap product(String sku, String... tags) {
        XdmArray tagArray = new XdmArray();
        for (String tag : tags) {
            tagArray = tagArray.addMember(new XdmAtomicValue(tag));
        }
        return new XdmMap()
                .put(new XdmAtomicValue("sku"), new XdmAtomicValue(sku))
                .put(new XdmAtomicValue("tags"), tagArray);
    }

    private static XdmMap product(XdmArray products, int index) {
        return (XdmMap) products.get(index).itemAt(0);
    }

    private static List<String> strings(XdmValue values) {
        XdmArray array = (XdmArray) values.itemAt(0);
        return array.asList().stream()
                .map(value -> value.itemAt(0).getStringValue())
                .toList();
    }
}
