/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_hive.hive_storage_api;

import java.util.HashMap;

import org.apache.hadoop.hive.common.io.DiskRangeList;
import org.apache.hadoop.hive.ql.util.IncrementalObjectSizeEstimator;
import org.apache.hadoop.hive.ql.util.IncrementalObjectSizeEstimator.ObjectEstimator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IncrementalObjectSizeEstimatorTest {
    @Test
    void createsEstimatorsFromObjectGraphAndReadsFields() {
        DiskRangeList first = new DiskRangeList(0, 4);
        DiskRangeList second = new DiskRangeList(4, 8);
        first.insertAfter(second);

        HashMap<Class<?>, ObjectEstimator> estimators =
            IncrementalObjectSizeEstimator.createEstimators(first);
        ObjectEstimator estimator = estimators.get(DiskRangeList.class);

        assertThat(estimator).isNotNull();
        assertThat(estimator.estimate(first, estimators)).isPositive();
        assertThat(estimators).containsKey(DiskRangeList.class);
    }

    @Test
    void addsEstimatorByClassName() {
        HashMap<Class<?>, ObjectEstimator> estimators = new HashMap<>();

        IncrementalObjectSizeEstimator.addEstimator(
            DiskRangeList.class.getName(), estimators, DiskRangeList.class);

        assertThat(estimators).containsKey(DiskRangeList.class);
        assertThat(estimators.get(DiskRangeList.class)
            .estimate(new DiskRangeList(8, 16), estimators))
            .isPositive();
    }
}
