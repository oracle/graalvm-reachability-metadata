/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_itextpdf.kernel;

import com.itextpdf.kernel.pdf.PdfName;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class KernelTest {
    @Test
    void staticPdfNames() {
        Assertions.assertTrue(PdfName.staticNames.size() > 800);
    }
}
