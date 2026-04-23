/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv_core.reader.xmlschema.XMLSchemaReader;
import org.junit.jupiter.api.Test;

public class XMLSchemaReaderDynamicAccessTest {
    @Test
    void loadsXmlSchemaSpecificAndSharedMessageBundles() {
        ExposedXMLSchemaReader reader = new ExposedXMLSchemaReader();

        assertThat(reader.localize("XMLSchemaReader.MaxOccursIsNecessary"))
                .contains("maxOccurs attribute is required");
        assertThat(reader.localize("GrammarReader.BadAttributeValue", "maxOccurs", "zero"))
                .contains("invalid value")
                .contains("maxOccurs")
                .contains("zero");
    }

    private static final class ExposedXMLSchemaReader extends XMLSchemaReader {
        private ExposedXMLSchemaReader() {
            super(MsvTestSupport.recordingController(), MsvTestSupport.namespaceAwareParserFactory());
        }

        private String localize(String propertyName, Object... arguments) {
            return localizeMessage(propertyName, arguments);
        }
    }
}
