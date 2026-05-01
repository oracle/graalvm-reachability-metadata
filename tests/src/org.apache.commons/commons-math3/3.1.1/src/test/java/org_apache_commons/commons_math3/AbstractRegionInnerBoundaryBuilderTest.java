/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_math3;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.commons.math3.geometry.euclidean.twod.Euclidean2D;
import org.apache.commons.math3.geometry.euclidean.twod.Line;
import org.apache.commons.math3.geometry.euclidean.twod.PolygonsSet;
import org.apache.commons.math3.geometry.euclidean.twod.Vector2D;
import org.apache.commons.math3.geometry.partitioning.BSPTree;
import org.apache.commons.math3.geometry.partitioning.BoundaryAttribute;
import org.apache.commons.math3.geometry.partitioning.SubHyperplane;
import org.junit.jupiter.api.Test;

public class AbstractRegionInnerBoundaryBuilderTest {
    @Test
    void buildsBoundaryAttributeWhenPlusSideIsOutside() {
        PolygonsSet region = halfPlaneRegion(Boolean.FALSE, Boolean.TRUE);

        BoundaryAttribute<Euclidean2D> attribute = boundaryAttribute(region);

        assertThat(attribute.getPlusOutside()).isNotNull();
        assertThat(attribute.getPlusInside()).isNull();
    }

    @Test
    void buildsBoundaryAttributeWhenPlusSideIsInside() {
        PolygonsSet region = halfPlaneRegion(Boolean.TRUE, Boolean.FALSE);

        BoundaryAttribute<Euclidean2D> attribute = boundaryAttribute(region);

        assertThat(attribute.getPlusOutside()).isNull();
        assertThat(attribute.getPlusInside()).isNotNull();
    }

    private static PolygonsSet halfPlaneRegion(boolean plusCellInside, boolean minusCellInside) {
        Line xAxis = new Line(Vector2D.ZERO, new Vector2D(1.0, 0.0));
        SubHyperplane<Euclidean2D> cut = xAxis.wholeHyperplane();
        BSPTree<Euclidean2D> tree = new BSPTree<Euclidean2D>(
                cut,
                new BSPTree<Euclidean2D>(plusCellInside),
                new BSPTree<Euclidean2D>(minusCellInside),
                null);
        return new PolygonsSet(tree);
    }

    @SuppressWarnings("unchecked")
    private static BoundaryAttribute<Euclidean2D> boundaryAttribute(PolygonsSet region) {
        return (BoundaryAttribute<Euclidean2D>) region.getTree(true).getAttribute();
    }
}
