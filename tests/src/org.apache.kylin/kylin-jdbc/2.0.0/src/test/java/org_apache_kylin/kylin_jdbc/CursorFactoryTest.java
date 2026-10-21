/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.util.Arrays;

import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.Meta.CursorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CursorFactoryTest {
    @Test
    void createsAndRestoresARecordCursorDescription() {
        CursorFactory factory = CursorFactory.record(Row.class, null, Arrays.asList("id", "name"));
        CursorFactory restored = CursorFactory.fromProto(factory.toProto());

        assertThat(factory.fieldNames).containsExactly("id", "name");
        assertThat(restored.clazz).isEqualTo(Row.class);
        assertThat(restored.fieldNames).containsExactly("id", "name");
    }

    public static class Row {
        public int id;
        public String name;
    }
}
