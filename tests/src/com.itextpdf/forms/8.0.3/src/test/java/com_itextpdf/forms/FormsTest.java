/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_itextpdf.forms;

import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.fields.CheckBoxFormFieldBuilder;
import com.itextpdf.forms.fields.PdfButtonFormField;
import com.itextpdf.forms.fields.merging.AlwaysThrowExceptionStrategy;
import com.itextpdf.forms.fields.merging.MergeFieldsStrategy;
import com.itextpdf.forms.fields.merging.OnDuplicateFormFieldNameStrategy;
import com.itextpdf.io.source.ByteArrayOutputStream;
import com.itextpdf.kernel.exceptions.PdfException;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FormsTest {
    @Test
    void defaultStrategy() {
        PdfDocument pdfDocument = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()));
        OnDuplicateFormFieldNameStrategy strategy = pdfDocument.getDiContainer()
                .getInstance(OnDuplicateFormFieldNameStrategy.class);
        Assertions.assertEquals(MergeFieldsStrategy.class, strategy.getClass());
    }

    @Test
    void alwaysThrowStrategy() {
        PdfDocument pdfDocument = new PdfDocument(new PdfWriter(new ByteArrayOutputStream()));
        PdfAcroForm form = PdfAcroForm.getAcroForm(pdfDocument, true, new AlwaysThrowExceptionStrategy());
        PdfButtonFormField field1 = new CheckBoxFormFieldBuilder(pdfDocument, "test").createCheckBox();
        form.addField(field1);
        PdfButtonFormField field2 = new CheckBoxFormFieldBuilder(pdfDocument, "test").createCheckBox();

        Exception exception = Assertions.assertThrows(PdfException.class, () -> form.addField(field2));
        Assertions.assertEquals("Field name test already exists in the form.", exception.getMessage());
    }
}
