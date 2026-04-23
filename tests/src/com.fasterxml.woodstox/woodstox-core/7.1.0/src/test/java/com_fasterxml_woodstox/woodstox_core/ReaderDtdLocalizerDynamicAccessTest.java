/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.ctc.wstx.shaded.msv_core.grammar.ExpressionPool;
import com.ctc.wstx.shaded.msv_core.reader.dtd.DTDReader;
import org.junit.jupiter.api.Test;

public class ReaderDtdLocalizerDynamicAccessTest {
    @Test
    void localizesDtdWarnings() throws Exception {
        MsvTestSupport.RecordingController controller = MsvTestSupport.recordingController();
        DTDReader reader = new DTDReader(controller, new ExpressionPool());

        reader.attributeDecl("book", "xmlns:demo", "CDATA", null, (short) 0, null);

        assertThat(controller.warnings())
                .singleElement()
                .satisfies(warning -> assertThat(warning).contains("DTD is attempting to declare xmlns"));
    }
}
