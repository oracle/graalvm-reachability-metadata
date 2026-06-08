/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_poi.poi_ooxml;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.poi.xdgf.usermodel.section.geometry.GeometryRow;
import org.apache.poi.xdgf.usermodel.section.geometry.GeometryRowFactory;
import org.apache.poi.xdgf.usermodel.section.geometry.LineTo;
import org.junit.jupiter.api.Test;

import com.microsoft.schemas.office.visio.x2012.main.CellType;
import com.microsoft.schemas.office.visio.x2012.main.RowType;

public class ObjectFactoryTest {

    @Test
    public void loadsRegisteredVisioGeometryRow() {
        RowType row = RowType.Factory.newInstance();
        row.setT("LineTo");
        addCell(row, "X", "1.25");
        addCell(row, "Y", "2.5");

        GeometryRow geometryRow = GeometryRowFactory.load(row);

        assertThat(geometryRow).isInstanceOf(LineTo.class);
        LineTo lineTo = (LineTo) geometryRow;
        assertThat(lineTo.getX()).isEqualTo(1.25D);
        assertThat(lineTo.getY()).isEqualTo(2.5D);
    }

    private static void addCell(RowType row, String name, String value) {
        CellType cell = row.addNewCell();
        cell.setN(name);
        cell.setV(value);
    }
}
