/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_poi.poi_ooxml_schemas;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.xmlbeans.SchemaType;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTSheet;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.WorkbookDocument;

public class SchemaorgApacheXmlbeansSystemSD023D6490046BA0250A839A9AD24C443TypeSystemHolderTest {
    @Test
    void createsSpreadsheetWorkbookDocumentThroughGeneratedXmlBeansFactory() {
        SchemaType workbookType = WorkbookDocument.type;
        WorkbookDocument document = WorkbookDocument.Factory.newInstance();

        CTWorkbook workbook = document.addNewWorkbook();
        CTSheet sheet = workbook.addNewSheets().addNewSheet();
        sheet.setName("Sheet1");
        sheet.setSheetId(1L);
        sheet.setId("rId1");

        assertThat(workbookType).isNotNull();
        assertThat(workbookType.getDocumentElementName().getLocalPart()).isEqualTo("workbook");
        assertThat(document.getWorkbook()).isNotNull();
        assertThat(workbook.getSheets().getSheetArray(0).getName()).isEqualTo("Sheet1");
        assertThat(sheet.getName()).isEqualTo("Sheet1");
        assertThat(document.xmlText()).contains("workbook", "sheets", "Sheet1", "rId1");
    }
}
