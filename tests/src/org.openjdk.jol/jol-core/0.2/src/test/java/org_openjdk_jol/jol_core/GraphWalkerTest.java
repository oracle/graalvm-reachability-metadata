/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_openjdk_jol.jol_core;

import org.junit.jupiter.api.Test;
import org.openjdk.jol.datamodel.DataModel;
import org.openjdk.jol.datamodel.X86_64_DataModel;
import org.openjdk.jol.info.GraphPathRecord;
import org.openjdk.jol.info.GraphWalker;
import org.openjdk.jol.layouters.RawLayouter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class GraphWalkerTest {
    @Test
    void walksReferencesDiscoveredFromLibraryObjectFields() {
        DataModel model = new X86_64_DataModel();
        RawLayouter layouter = new RawLayouter(model);
        GraphWalker walker = new GraphWalker(layouter);
        List<GraphPathRecord> records = new ArrayList<GraphPathRecord>();

        walker.addVisitor(records::add);
        walker.walk();

        assertThat(records)
                .extracting(GraphPathRecord::path)
                .containsExactly("", ".model");
        assertThat(records)
                .extracting(GraphPathRecord::obj)
                .containsExactly(layouter, model);
        assertThat(records)
                .extracting(GraphPathRecord::depth)
                .containsExactly(0, 1);
    }
}
