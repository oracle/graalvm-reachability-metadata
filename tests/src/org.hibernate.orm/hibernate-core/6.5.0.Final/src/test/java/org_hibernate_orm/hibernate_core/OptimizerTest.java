/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import org.hibernate.id.enhanced.Optimizer;
import org.hibernate.id.enhanced.OptimizerFactory;
import org.hibernate.id.enhanced.StandardOptimizerDescriptor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OptimizerTest {

    @Test
    public void testOptimizers() {
        for (StandardOptimizerDescriptor optimizerDescriptor : StandardOptimizerDescriptor.values()) {
            Optimizer optimizer = OptimizerFactory.buildOptimizer(
                    optimizerDescriptor,
                    Long.class,
                    50,
                    1);
            assertThat(optimizer).isNotNull();
        }
    }
}
