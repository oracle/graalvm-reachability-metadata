/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_poi.poi_ooxml_schemas;

import org.apache.xmlbeans.XmlException;
import org.junit.jupiter.api.Test;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTSheet;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTSheets;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.WorkbookDocument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class OoxmlTypeSystemHolderTest {
    @Test
    void roundTripsWorkbookSheetRelationships() throws XmlException {
        WorkbookDocument document = WorkbookDocument.Factory.newInstance();
        CTSheets sheets = document.addNewWorkbook().addNewSheets();
        CTSheet summary = sheets.addNewSheet();
        summary.setName("Summary");
        summary.setSheetId(1);
        summary.setId("rId1");
        CTSheet details = sheets.addNewSheet();
        details.setName("Details");
        details.setSheetId(2);
        details.setId("rId2");

        assertTrue(document.validate());

        WorkbookDocument parsed = WorkbookDocument.Factory.parse(document.xmlText());
        CTSheets parsedSheets = parsed.getWorkbook().getSheets();
        assertEquals(2, parsedSheets.sizeOfSheetArray());
        assertEquals("Summary", parsedSheets.getSheetArray(0).getName());
        assertEquals(1, parsedSheets.getSheetArray(0).getSheetId());
        assertEquals("rId1", parsedSheets.getSheetArray(0).getId());
        assertEquals("Details", parsedSheets.getSheetArray(1).getName());
        assertEquals(2, parsedSheets.getSheetArray(1).getSheetId());
        assertEquals("rId2", parsedSheets.getSheetArray(1).getId());
        assertTrue(parsed.validate());
    }
}
