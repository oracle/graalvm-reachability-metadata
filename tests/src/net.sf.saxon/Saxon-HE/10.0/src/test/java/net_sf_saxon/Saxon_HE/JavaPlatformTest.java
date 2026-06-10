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
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaPlatformTest {
    private static final String TRY_JDK9_FIELD = "tryJdk9";

    @Test
    void loadsParserForXmlFragmentsThatUsesSuppliedEntityResolver() throws Exception {
        XMLReader reader = new JavaPlatform().loadParserForXmlFragments();

        assertThat(parseWithExternalEntity(reader)).containsExactly("root", "child");
    }

    @Test
    void loadsParserForXmlFragmentsWhenJdk9DefaultParserLookupIsUnavailable() throws Exception {
        boolean originalTryJdk9 = setTryJdk9(false);
        try {
            XMLReader reader = new JavaPlatform().loadParserForXmlFragments();

            assertThat(parseWithExternalEntity(reader)).containsExactly("root", "child");
        } finally {
            setTryJdk9(originalTryJdk9);
        }
    }

    private static List<String> parseWithExternalEntity(XMLReader reader) throws Exception {
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
        return elementNames;
    }

    private static boolean setTryJdk9(boolean value) throws Exception {
        Field tryJdk9 = JavaPlatform.class.getDeclaredField(TRY_JDK9_FIELD);
        tryJdk9.setAccessible(true);
        boolean previous = tryJdk9.getBoolean(null);
        tryJdk9.setBoolean(null, value);
        return previous;
    }
}
