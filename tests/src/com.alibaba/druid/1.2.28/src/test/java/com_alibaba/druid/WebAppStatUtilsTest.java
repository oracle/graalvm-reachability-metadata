/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.support.http.stat.WebAppStat;
import com.alibaba.druid.support.http.stat.WebAppStatUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class WebAppStatUtilsTest {
    @Test
    void readsAndResetsSubclassStatistics() {
        DerivedWebAppStat stat = new DerivedWebAppStat();
        stat.beforeInvoke();
        stat.getURIStat("/orders", true);
        stat.getSessionStat("session-1", true);
        stat.afterInvoke(null, 1_000L);

        assertNotNull(WebAppStatUtils.getStatData(stat));
        assertNotNull(WebAppStatUtils.getURIStatDataList(stat));
        assertNotNull(WebAppStatUtils.getSessionStatDataList(stat));
        assertNotNull(WebAppStatUtils.getURIStatData(stat, "/orders"));
        assertNotNull(WebAppStatUtils.getSessionStatData(stat, "session-1"));
        WebAppStatUtils.reset(stat);
        assertNotNull(WebAppStatUtils.getStatData(stat));
    }

    public static final class DerivedWebAppStat extends WebAppStat {
        DerivedWebAppStat() {
            super("/test");
        }
    }
}
