/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.druid;

import com.alibaba.druid.support.http.stat.WebAppStat;
import com.alibaba.druid.support.http.stat.WebAppStatUtils;
import com.alibaba.druid.support.http.stat.WebSessionStat;
import com.alibaba.druid.support.http.stat.WebURIStat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Timeout(120)
public class WebAppStatUtilsTest {
    @Test
    void readsAndResetsApplicationSessionAndUriCounters() {
        ApplicationStats application = new ApplicationStats();
        WebURIStat uri = application.getURIStat("/catalog", true);
        WebSessionStat session = application.getSessionStat("customer-session", true);
        application.beforeInvoke();
        uri.beforeInvoke();
        session.beforeInvoke();
        session.afterInvoke(null, 1000000);
        uri.afterInvoke(null, 1000000);
        application.afterInvoke(null, 1000000);
        assertEquals(1L, WebAppStatUtils.getStatData(application).get("RequestCount"));
        assertNotNull(WebAppStatUtils.getURIStatData(application, "/catalog"));
        assertEquals(1L, WebAppStatUtils.getURIStatData(application, "/catalog").get("RequestCount"));
        assertEquals(1, WebAppStatUtils.getURIStatDataList(application).size());
        assertEquals(1L, WebAppStatUtils.getSessionStatData(application, "customer-session").get("RequestCount"));
        assertEquals(1, WebAppStatUtils.getSessionStatDataList(application).size());
        WebAppStatUtils.reset(application);
        assertEquals(0L, WebAppStatUtils.getStatData(application).get("RequestCount"));
        assertEquals(0, WebAppStatUtils.getURIStatDataList(application).size());
        assertEquals(0, WebAppStatUtils.getSessionStatDataList(application).size());
        assertEquals(0L, session.getRequestCount());
    }

    public static class ApplicationStats extends WebAppStat {
        public ApplicationStats() {
            super("/shop");
        }
    }
}
