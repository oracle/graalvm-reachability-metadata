/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv_core.reader.trex.ng.RELAXNGReader;
import org.junit.jupiter.api.Test;

public class RELAXNGReaderDynamicAccessTest {
    @Test
    void loadsRelaxNgReaderMessages() {
        ExposedRELAXNGReader reader = new ExposedRELAXNGReader();

        assertThat(reader.localize("RELAXNGReader.NotAbsoluteURI", "relative/path"))
                .contains("not an absolute URI")
                .contains("relative/path");
    }

    private static final class ExposedRELAXNGReader extends RELAXNGReader {
        private ExposedRELAXNGReader() {
            super(MsvTestSupport.recordingController());
        }

        private String localize(String propertyName, Object... arguments) {
            return localizeMessage(propertyName, arguments);
        }
    }
}
