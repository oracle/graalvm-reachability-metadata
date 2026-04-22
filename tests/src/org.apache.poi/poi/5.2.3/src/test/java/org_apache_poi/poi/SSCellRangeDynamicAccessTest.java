/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_poi.poi;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellRange;
import org.apache.poi.ss.util.CellRangeAddress;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SSCellRangeDynamicAccessTest {

    @Test
    void createsAndMaterializesArrayFormulaRanges() throws Exception {
        try (HSSFWorkbook workbook = new HSSFWorkbook()) {
            HSSFSheet sheet = workbook.createSheet("ArrayFormula");
            CellRange<HSSFCell> cells = sheet.setArrayFormula("SUM(C1:D2)", new CellRangeAddress(0, 1, 0, 1));

            HSSFCell[][] matrix = cells.getCells();

            assertThat(cells.getReferenceText()).isEqualTo("A1:B2");
            assertThat(cells.getFlattenedCells()).hasSize(4);
            assertThat(matrix).hasSize(2);
        }
    }
}
