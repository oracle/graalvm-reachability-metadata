/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_woodstox.woodstox_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ctc.wstx.shaded.msv_core.scanner.dtd.DTDParser;
import java.io.StringReader;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

public class MessageCatalogDynamicAccessTest {
    @Test
    void negotiatesSupportedLocalesAndLoadsDtdMessages() throws Exception {
        DTDParser parser = new DTDParser();

        assertThat(parser.chooseLocale(new String[]{"en_US", "zz_ZZ"})).isEqualTo(Locale.US);
        assertThat(parser.getLocale()).isEqualTo(Locale.US);
        assertThatThrownBy(() -> parser.parse(new InputSource(new StringReader("<!ELEMENTroot EMPTY>"))))
                .hasMessageContaining("whitespace");
    }
}
