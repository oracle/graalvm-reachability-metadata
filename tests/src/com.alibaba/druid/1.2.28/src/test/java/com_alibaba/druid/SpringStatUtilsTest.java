/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.support.spring.stat.SpringMethodInfo;
import com.alibaba.druid.support.spring.stat.SpringMethodStat;
import com.alibaba.druid.support.spring.stat.SpringStat;
import com.alibaba.druid.support.spring.stat.SpringStatUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class SpringStatUtilsTest {
    @Test
    void listsAndResetsServiceInvocationStatistics() throws Exception {
        ServiceStats stats = new ServiceStats();
        SpringMethodInfo methodInfo = new SpringMethodInfo(CatalogService.class,
                CatalogService.class.getMethod("lookup"));
        SpringMethodStat method = stats.getMethodStat(methodInfo, true);
        method.beforeInvoke();
        try {
            assertEquals("catalog", new CatalogService().lookup());
        } finally {
            method.afterInvoke(null, 1000000);
        }
        List<Map<String, Object>> data = SpringStatUtils.getMethodStatDataList(stats);
        assertEquals(1, data.size());
        assertEquals(1L, data.get(0).get("ExecuteCount"));
        SpringStatUtils.reset(stats);
        assertTrue(SpringStatUtils.getMethodStatDataList(stats).isEmpty());
        assertEquals(0, method.getExecuteCount());
    }

    public static class ServiceStats extends SpringStat {
    }

    public static class CatalogService {
        public String lookup() {
            return "catalog";
        }
    }
}
