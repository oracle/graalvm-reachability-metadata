/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv_core.reader.trex.classic.TREXGrammarReader;
import org.junit.jupiter.api.Test;

public class TREXGrammarReaderDynamicAccessTest {
    @Test
    void loadsTrexGrammarReaderMessages() {
        ExposedTREXGrammarReader reader = new ExposedTREXGrammarReader();

        assertThat(reader.localize("TREXGrammarReader.DuplicateDefinition", "patternName"))
                .contains("defined more than once")
                .contains("patternName");
    }

    private static final class ExposedTREXGrammarReader extends TREXGrammarReader {
        private ExposedTREXGrammarReader() {
            super(MsvTestSupport.recordingController());
        }

        private String localize(String propertyName, Object... arguments) {
            return localizeMessage(propertyName, arguments);
        }
    }
}
