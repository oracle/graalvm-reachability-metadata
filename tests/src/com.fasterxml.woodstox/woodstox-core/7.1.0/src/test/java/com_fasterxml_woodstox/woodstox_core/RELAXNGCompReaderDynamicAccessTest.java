/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv_core.reader.trex.ng.comp.RELAXNGCompReader;
import org.junit.jupiter.api.Test;

public class RELAXNGCompReaderDynamicAccessTest {
    @Test
    void loadsRelaxNgCompatibilityMessages() {
        ExposedRELAXNGCompReader reader = new ExposedRELAXNGCompReader();

        assertThat(reader.localize("RELAXNGReader.Compatibility.Annotation.InvalidAttribute", "foreign"))
                .contains("annotation element")
                .contains("foreign");
    }

    private static final class ExposedRELAXNGCompReader extends RELAXNGCompReader {
        private ExposedRELAXNGCompReader() {
            super(MsvTestSupport.recordingController());
        }

        private String localize(String propertyName, Object... arguments) {
            return localizeMessage(propertyName, arguments);
        }
    }
}
