/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pdfbox.pdfbox;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/// Exercises PDFBox's interactive form subsystem to fill and reload a customer data form.
public class AcroFormDataEntryTest {
    @Test
    @Timeout(60)
    public void savesAndReloadsCustomerFormFields() throws IOException {
        byte[] completedForm;
        try (PDDocument formDocument = new PDDocument()) {
            PDAcroForm form = new PDAcroForm(formDocument);
            formDocument.getDocumentCatalog().setAcroForm(form);
            PDResources resources = new PDResources();
            resources.put(COSName.HELV, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            form.setDefaultResources(resources);
            form.setDefaultAppearance("/Helv 12 Tf 0 g");

            PDTextField customerName = new PDTextField(form);
            customerName.setPartialName("customerName");
            form.getFields().add(customerName);

            PDTextField emailAddress = new PDTextField(form);
            emailAddress.setPartialName("emailAddress");
            form.getFields().add(emailAddress);

            customerName.setValue("Ada Lovelace");
            emailAddress.setValue("ada@example.test");

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            formDocument.save(output);
            completedForm = output.toByteArray();
        }

        try (PDDocument loadedFormDocument = Loader.loadPDF(completedForm)) {
            PDAcroForm loadedForm = loadedFormDocument.getDocumentCatalog().getAcroForm();
            assertNotNull(loadedForm);
            assertEquals("Ada Lovelace", loadedForm.getField("customerName").getValueAsString());
            assertEquals("ada@example.test", loadedForm.getField("emailAddress").getValueAsString());
        }
    }
}
