/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_pulsar.pulsar_client_all;

import org.apache.pulsar.shade.com.yahoo.sketches.tuple.DoubleSummary;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.DoubleSummaryFactory;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.UpdatableSketch;
import org.apache.pulsar.shade.com.yahoo.sketches.tuple.UpdatableSketchBuilder;

public final class TupleSketchSupport {
    private TupleSketchSupport() {
    }

    public static UpdatableSketch<Double, DoubleSummary> sketch(String... keys) {
        UpdatableSketch<Double, DoubleSummary> sketch =
                new UpdatableSketchBuilder<Double, DoubleSummary>(new DoubleSummaryFactory())
                        .setNominalEntries(16)
                        .build();
        for (int i = 0; i < keys.length; i++) {
            sketch.update(keys[i], (double) (i + 1));
        }
        return sketch;
    }
}
