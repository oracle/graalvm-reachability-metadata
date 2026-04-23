/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv_core.grammar.ExpressionPool;
import com.ctc.wstx.shaded.msv_core.reader.trex.classic.TREXGrammarReader;
import org.junit.jupiter.api.Test;

public class TREXBaseReaderDynamicAccessTest {
    @Test
    void loadsTrexSpecificAndSharedMessageBundles() {
        ExposedTrexBaseReader reader = new ExposedTrexBaseReader();

        assertThat(reader.localize("TREXGrammarReader.UndefinedPattern", "missingPattern"))
                .contains("undefined pattern")
                .contains("missingPattern");
        assertThat(reader.localize("GrammarReader.MissingAttribute", "grammar", "name"))
                .contains("attribute is required")
                .contains("grammar")
                .contains("name");
    }

    private static final class ExposedTrexBaseReader extends TREXGrammarReader {
        private ExposedTrexBaseReader() {
            super(
                    MsvTestSupport.recordingController(),
                    MsvTestSupport.namespaceAwareParserFactory(),
                    new ExpressionPool());
        }

        private String localize(String propertyName, Object... arguments) {
            return super.localizeMessage(propertyName, arguments);
        }
    }
}
