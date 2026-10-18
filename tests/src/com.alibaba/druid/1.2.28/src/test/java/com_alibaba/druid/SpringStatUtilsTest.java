/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.support.spring.stat.SpringStat;
import com.alibaba.druid.support.spring.stat.SpringStatUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SpringStatUtilsTest {
    @Test
    void readsAndResetsSubclassStatistics() {
        DerivedSpringStat stat = new DerivedSpringStat();

        assertNotNull(SpringStatUtils.getMethodStatDataList(stat));
        assertNull(SpringStatUtils.getMethodStatData(stat, "example.Service", "run()"));
        SpringStatUtils.reset(stat);
        assertNotNull(SpringStatUtils.getMethodStatDataList(stat));
    }

    public static final class DerivedSpringStat extends SpringStat {
    }
}
