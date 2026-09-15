/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_librepdf.openpdf;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import com.lowagie.text.Document;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.error_messages.MessageLocalization;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfWriter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the built-in Type1 ("standard 14") fonts, whose AFM metrics and glyph list are
 * loaded from classpath resources, and the localized error-message catalog.
 */
class OpenpdfTest {

    private static final String[] STANDARD_14 = {
            BaseFont.COURIER, BaseFont.COURIER_BOLD, BaseFont.COURIER_BOLDOBLIQUE, BaseFont.COURIER_OBLIQUE,
            BaseFont.HELVETICA, BaseFont.HELVETICA_BOLD, BaseFont.HELVETICA_BOLDOBLIQUE, BaseFont.HELVETICA_OBLIQUE,
            BaseFont.SYMBOL,
            BaseFont.TIMES_BOLD, BaseFont.TIMES_BOLDITALIC, BaseFont.TIMES_ITALIC, BaseFont.TIMES_ROMAN,
            BaseFont.ZAPFDINGBATS,
    };

    @Test
    void loadsAllStandard14FontMetrics() throws Exception {
        for (String fontName : STANDARD_14) {
            BaseFont font = BaseFont.createFont(fontName, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            assertThat(font.getWidth("A")).as("width of 'A' in %s", fontName).isPositive();
        }
    }

    @Test
    void writesPdfDocumentWithBuiltInFont() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, out);
        document.open();
        document.add(new Paragraph("Hello from a GraalVM native image",
                FontFactory.getFont(FontFactory.HELVETICA, 12)));
        document.close();

        String content = new String(out.toByteArray(), StandardCharsets.ISO_8859_1);
        assertThat(content).startsWith("%PDF");
        assertThat(content).contains("Helvetica");
    }

    @Test
    void resolvesLocalizedErrorMessages() {
        // Resolved from the com/lowagie/text/error_messages/<language>.lng catalog resource;
        // without it every message degrades to "No message found for ...".
        assertThat(MessageLocalization.getComposedMessage("document.already.pre.closed"))
                .isEqualTo("Document already pre closed.");
    }
}
