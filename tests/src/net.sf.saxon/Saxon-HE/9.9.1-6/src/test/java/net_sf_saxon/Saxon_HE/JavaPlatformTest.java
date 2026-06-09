/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_saxon.Saxon_HE;

import net.sf.saxon.java.JavaPlatform;

import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaPlatformTest {
    @Test
    void loadsParserForXmlFragmentsThatUsesSuppliedEntityResolver() throws Exception {
        XMLReader reader = new JavaPlatform().loadParserForXmlFragments();
        List<String> elementNames = new ArrayList<>();
        reader.setContentHandler(new DefaultHandler() {
            @Override
            public void startElement(String uri, String localName, String qName, Attributes attributes) {
                elementNames.add(qName);
            }
        });
        reader.setEntityResolver((publicId, systemId) -> {
            if ("urn:saxon-test-fragment".equals(systemId)) {
                return new InputSource(new StringReader("<child>fragment</child>"));
            }
            return null;
        });

        reader.parse(new InputSource(new StringReader("""
                <!DOCTYPE root [<!ENTITY fragment SYSTEM "urn:saxon-test-fragment">]>
                <root>&fragment;</root>
                """)));

        assertThat(elementNames).containsExactly("root", "child");
    }
}
