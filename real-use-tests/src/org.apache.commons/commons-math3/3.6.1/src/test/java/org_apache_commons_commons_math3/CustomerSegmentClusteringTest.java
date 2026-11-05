/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons_commons_math3;

import java.util.Comparator;
import java.util.List;

import org.apache.commons.math3.ml.clustering.CentroidCluster;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import org.apache.commons.math3.ml.clustering.KMeansPlusPlusClusterer;
import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.apache.commons.math3.random.MersenneTwister;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Customer segmentation groups application users by their activity and spend with Commons Math clustering.
public class CustomerSegmentClusteringTest {
    @Test
    public void groupsCustomersIntoActionableSegments() {
        List<DoublePoint> customers = List.of(
                new DoublePoint(new double[] {2.0, 20.0}),
                new DoublePoint(new double[] {3.0, 18.0}),
                new DoublePoint(new double[] {4.0, 21.0}),
                new DoublePoint(new double[] {18.0, 4.0}),
                new DoublePoint(new double[] {20.0, 5.0}),
                new DoublePoint(new double[] {19.0, 7.0}));
        KMeansPlusPlusClusterer<DoublePoint> segmenter = new KMeansPlusPlusClusterer<>(2, 100,
                new EuclideanDistance(), new MersenneTwister(42L));

        List<CentroidCluster<DoublePoint>> segments = segmenter.cluster(customers).stream()
                .map(cluster -> (CentroidCluster<DoublePoint>) cluster)
                .sorted(Comparator.comparingDouble(cluster -> cluster.getCenter().getPoint()[0]))
                .toList();

        assertEquals(2, segments.size());
        assertEquals(3, segments.get(0).getPoints().size());
        assertEquals(3, segments.get(1).getPoints().size());
        assertEquals(3.0, segments.get(0).getCenter().getPoint()[0], 1.0e-10);
        assertEquals(19.0, segments.get(1).getCenter().getPoint()[0], 1.0e-10);
        assertEquals(19.6666666667, segments.get(0).getCenter().getPoint()[1], 1.0e-10);
        assertEquals(5.3333333333, segments.get(1).getCenter().getPoint()[1], 1.0e-10);
    }
}
