/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pdfbox.fontbox;

import org.apache.fontbox.cmap.CMap;
import org.apache.fontbox.cmap.CMapParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CMapParserTest {
    @Test
    void parsesPredefinedCMapFromPackagedResource() throws Exception {
        CMap cmap = new CMapParser().parsePredefined("Identity-H");

        assertThat(cmap.getName()).isEqualTo("Identity-H");
        assertThat(cmap.getRegistry()).isEqualTo("Adobe");
        assertThat(cmap.getOrdering()).isEqualTo("Identity");
        assertThat(cmap.getSupplement()).isZero();
        assertThat(cmap.getType()).isEqualTo(1);
        assertThat(cmap.getWMode()).isZero();
        assertThat(cmap.hasCIDMappings()).isTrue();
        assertThat(cmap.toCID(new byte[] {0x12, 0x34 })).isEqualTo(0x1234);
    }
}
