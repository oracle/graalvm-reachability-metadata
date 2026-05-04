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
        BSPTree<Euclidean2D> tree = singleCutTree(false, true);
        PolygonsSet polygon = new PolygonsSet(tree);

        BSPTree<Euclidean2D> treeWithBoundary = polygon.getTree(true);

        BoundaryAttribute<Euclidean2D> attribute = boundaryAttributeOf(treeWithBoundary);
        assertThat(attribute.getPlusOutside()).isNotNull();
        assertThat(attribute.getPlusInside()).isNull();
    }

    @Test
    void buildsBoundaryAttributeWhenPlusSideIsInside() {
        BSPTree<Euclidean2D> tree = singleCutTree(true, false);
        PolygonsSet polygon = new PolygonsSet(tree);

        BSPTree<Euclidean2D> treeWithBoundary = polygon.getTree(true);

        BoundaryAttribute<Euclidean2D> attribute = boundaryAttributeOf(treeWithBoundary);
        assertThat(attribute.getPlusOutside()).isNull();
        assertThat(attribute.getPlusInside()).isNotNull();
    }

    private static BSPTree<Euclidean2D> singleCutTree(boolean plusInside, boolean minusInside) {
        SubHyperplane<Euclidean2D> cut = new Line(Vector2D.ZERO, new Vector2D(1.0, 0.0)).wholeHyperplane();
        return new BSPTree<Euclidean2D>(
                cut,
                new BSPTree<Euclidean2D>(plusInside),
                new BSPTree<Euclidean2D>(minusInside),
                null);
    }

    @SuppressWarnings("unchecked")
    private static BoundaryAttribute<Euclidean2D> boundaryAttributeOf(BSPTree<Euclidean2D> tree) {
        assertThat(tree.getAttribute()).isInstanceOf(BoundaryAttribute.class);
        return (BoundaryAttribute<Euclidean2D>) tree.getAttribute();
    }
}
