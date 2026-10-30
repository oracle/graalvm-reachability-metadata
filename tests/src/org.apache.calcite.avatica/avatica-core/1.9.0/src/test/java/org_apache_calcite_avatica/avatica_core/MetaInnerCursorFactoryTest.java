/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite_avatica.avatica_core;

import org.apache.calcite.avatica.Meta.CursorFactory;
import org.apache.calcite.avatica.Meta.Style;
import org.apache.calcite.avatica.MetaImpl;
import org.apache.calcite.avatica.MetaImpl.MetaSchema;
import org.apache.calcite.avatica.proto.Common;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MetaInnerCursorFactoryTest {
    @Test
    void roundTripsProjectedRecordFactoryThroughProtobuf() throws Exception {
        CursorFactory original = CursorFactory.record(MetaSchema.class, null, List.of("tableCatalog", "tableSchem"));
        Common.CursorFactory wire = Common.CursorFactory.parseFrom(original.toProto().toByteArray());
        CursorFactory restored = CursorFactory.fromProto(wire);
        assertEquals(original, restored);
        assertEquals(Style.RECORD_PROJECTION, restored.style);
        List<Object> input = List.of(new MetaSchema("analytics", "metrics"));
        assertEquals(List.of(List.of("analytics", "metrics")), MetaImpl.collect(restored, input, new ArrayList<>()));
    }

    @Test
    void roundTripsFullRecordFactoryThroughProtobuf() {
        CursorFactory original = CursorFactory.record(MetaSchema.class);
        assertEquals(original, CursorFactory.fromProto(original.toProto()));
    }
}
