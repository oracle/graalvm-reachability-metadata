/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.util.AutoPopulatingList;
import org.springframework.util.StopWatch;

public class AutoPopulatingListInnerReflectiveElementFactoryTest {

    @Test
    void getCreatesElementsThroughReflectiveElementFactory() {
        AutoPopulatingList<StopWatch> stopWatches = new AutoPopulatingList<>(StopWatch.class);

        StopWatch third = stopWatches.get(2);
        Object[] valuesWithGap = stopWatches.toArray();
        StopWatch first = stopWatches.get(0);

        assertThat(third).isInstanceOf(StopWatch.class);
        assertThat(first).isInstanceOf(StopWatch.class);
        assertThat(first).isNotSameAs(third);
        assertThat(valuesWithGap).containsExactly(null, null, third);
        assertThat(stopWatches).hasSize(3);
        assertThat(stopWatches.get(0)).isSameAs(first);
        assertThat(stopWatches.get(2)).isSameAs(third);
    }
}
