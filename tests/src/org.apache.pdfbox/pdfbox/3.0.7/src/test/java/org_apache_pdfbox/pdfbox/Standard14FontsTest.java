/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pdfbox.pdfbox;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.fontbox.afm.FontMetrics;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.junit.jupiter.api.Test;

public class Standard14FontsTest {
    @Test
    void getAfmLoadsStandardFontMetricsResource() {
        FontMetrics metrics = Standard14Fonts.getAFM(FontName.HELVETICA.getName());

        assertThat(metrics).isNotNull();
        assertThat(metrics.getFontName()).isEqualTo(FontName.HELVETICA.getName());
        assertThat(metrics.getFamilyName()).isEqualTo("Helvetica");
        assertThat(metrics.getCharacterWidth("A")).isGreaterThan(0);
    }
}
