/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_netty.netty;

import org.jboss.netty.util.DefaultObjectSizeEstimator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultObjectSizeEstimatorTest {
    @Test
    void estimatesCustomObjectsByWalkingDeclaredFields() {
        DefaultObjectSizeEstimator estimator = new DefaultObjectSizeEstimator();

        int size = estimator.estimateSize(new SampleValue());

        assertThat(size).isGreaterThan(0);
    }

    private static final class SampleValue {
        private final long id = 1;
        private final String name = "netty";
    }
}
