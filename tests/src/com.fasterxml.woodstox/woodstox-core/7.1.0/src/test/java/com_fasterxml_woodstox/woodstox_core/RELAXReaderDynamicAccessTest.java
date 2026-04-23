/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv_core.grammar.ExpressionPool;
import com.ctc.wstx.shaded.msv_core.reader.relax.core.RELAXCoreReader;
import org.junit.jupiter.api.Test;

public class RELAXReaderDynamicAccessTest {
    @Test
    void loadsRelaxSpecificAndSharedMessageBundles() {
        ExposedRelaxReader reader = new ExposedRelaxReader();

        assertThat(reader.localize("RELAXReader.IllegalOccurs", "twice"))
                .contains("occurs")
                .contains("twice");
        assertThat(reader.localize("GrammarReader.BadAttributeValue", "minOccurs", "many"))
                .contains("invalid value")
                .contains("minOccurs")
                .contains("many");
    }

    private static final class ExposedRelaxReader extends RELAXCoreReader {
        private ExposedRelaxReader() {
            super(
                    MsvTestSupport.recordingController(),
                    MsvTestSupport.namespaceAwareParserFactory(),
                    new ExpressionPool());
        }

        private String localize(String propertyName, Object... arguments) {
            return localizeMessage(propertyName, arguments);
        }
    }
}
