/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pdfbox.pdfbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.pdfbox.pdmodel.font.encoding.GlyphList;
import org.junit.jupiter.api.Test;

public class GlyphListTest {
    @Test
    void builtInGlyphListsLoadBundledResourceMappings() {
        GlyphList adobeGlyphList = GlyphList.getAdobeGlyphList();
        GlyphList zapfDingbats = GlyphList.getZapfDingbats();

        assertThat(adobeGlyphList.toUnicode("A")).isEqualTo("A");
        assertThat(adobeGlyphList.toUnicode("space")).isEqualTo(" ");
        assertThat(adobeGlyphList.codePointToName('A')).isEqualTo("A");

        assertThat(zapfDingbats.toUnicode("a1")).isEqualTo("\u2701");
        assertThat(zapfDingbats.codePointToName(0x2701)).isEqualTo("a1");
    }
}
