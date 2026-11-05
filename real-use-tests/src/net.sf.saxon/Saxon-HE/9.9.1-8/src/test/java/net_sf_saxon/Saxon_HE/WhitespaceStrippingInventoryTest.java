/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.WhitespaceStrippingPolicy;
import net.sf.saxon.s9api.XdmNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises Saxon's whitespace-stripping subsystem to read formatted inventory records.
 *
 * An inventory service accepts human-readable XML feeds and strips formatting whitespace during
 * parsing so its downstream record reader sees only the product elements supplied by the sender.
 */
public class WhitespaceStrippingInventoryTest {

    private static final String INVENTORY = """
            <inventory>
              <item sku="NOTEBOOK-42" quantity="18"/>
              <item sku="PENS-7" quantity="4"/>
            </inventory>
            """;

    @Test
    @Timeout(60)
    public void readsInventoryElementsWithoutFormattingNodes() throws Exception {
        Processor processor = new Processor(false);
        DocumentBuilder documentBuilder = processor.newDocumentBuilder();
        documentBuilder.setWhitespaceStrippingPolicy(WhitespaceStrippingPolicy.ALL);

        XdmNode document = documentBuilder.build(
                new StreamSource(new StringReader(INVENTORY)));
        XdmNode inventory = document.children().iterator().next();
        List<String> records = new ArrayList<>();
        for (XdmNode child : inventory.children()) {
            records.add(child.getNodeName().getLocalName()
                    + ":" + child.attribute("sku")
                    + "=" + child.attribute("quantity"));
        }

        assertEquals(List.of("item:NOTEBOOK-42=18", "item:PENS-7=4"), records);
    }
}
