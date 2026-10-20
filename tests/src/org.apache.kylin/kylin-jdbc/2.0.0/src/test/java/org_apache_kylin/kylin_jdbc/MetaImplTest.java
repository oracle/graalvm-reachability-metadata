/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.Meta.CursorFactory;
import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.MetaImpl;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MetaImplTest {
    @Test
    void collectsPublicRecordFieldsIntoRows() {
        List<Object> records = Arrays.<Object>asList(new Row(3, "north"), new Row(4, "south"));

        List<List<Object>> rows = MetaImpl.collect(CursorFactory.record(Row.class), records, new ArrayList<>());

        assertThat(rows).containsExactly(Arrays.<Object>asList(3, "north"), Arrays.<Object>asList(4, "south"));
    }

    public static class Row {
        public int id;
        public String region;

        public Row(int id, String region) {
            this.id = id;
            this.region = region;
        }
    }
}
