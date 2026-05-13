/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package nekohtml.nekohtml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.StringReader;

import org.apache.xerces.xni.parser.XMLDocumentFilter;
import org.apache.xerces.xni.parser.XMLDocumentSource;
import org.apache.xerces.xni.parser.XMLInputSource;
import org.cyberneko.html.HTMLConfiguration;
import org.cyberneko.html.filters.DefaultFilter;
import org.junit.jupiter.api.Test;

public class HTMLConfigurationTest {
    private static final String FILTERS = "http://cyberneko.org/html/properties/filters";

    @Test
    void resetConnectsConfiguredFiltersThroughDocumentSourceMethod() throws Exception {
        HTMLConfiguration configuration = new HTMLConfiguration();
        RecordingDocumentFilter filter = new RecordingDocumentFilter();
        XMLDocumentFilter[] filters = { filter };

        configuration.setProperty(FILTERS, filters);
        configuration.setInputSource(new XMLInputSource(null, "memory:sample.html", null,
                        new StringReader("<html><body>text</body></html>"), "UTF-8"));

        assertEquals(1, filter.setDocumentSourceCount);
        assertNotNull(filter.documentSource);
        assertSame(filter.documentSource, filter.getDocumentSource());
    }

    public static class RecordingDocumentFilter extends DefaultFilter {
        int setDocumentSourceCount;
        XMLDocumentSource documentSource;

        @Override
        public void setDocumentSource(XMLDocumentSource source) {
            setDocumentSourceCount++;
            documentSource = source;
            super.setDocumentSource(source);
        }
    }
}
