/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.constructs.web.filter.CachingFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
public class CachingFilterTest {
    @TempDir
    Path directory;

    @Test
    void cachesPageAndReplaysBodyHeadersAndCookiesWithoutCallingTheChainAgain() throws Exception {
        CacheManager manager = TestSupport.manager(directory);
        manager.addCache("pages");
        Filter filter = new OrderPageFilter(manager);
        try {
            filter.init(new WebContext());
            AtomicInteger renders = new AtomicInteger();
            FilterChain chain = (request, response) -> {
                renders.incrementAndGet();
                HttpServletResponse http = (HttpServletResponse) response;
                http.setContentType("text/plain");
                http.addHeader("X-Page", "orders");
                http.addCookie(new Cookie("view", "summary"));
                http.getWriter().write("cached order summary");
            };
            WebResponse first = new WebResponse();
            filter.doFilter(new WebRequest(), first, chain);
            assertEquals("cached order summary", first.body.toString(StandardCharsets.UTF_8));

            WebResponse cached = new WebResponse();
            filter.doFilter(new WebRequest(), cached, chain);
            assertEquals(1, renders.get());
            assertEquals("cached order summary", cached.body.toString(StandardCharsets.UTF_8));
            assertEquals(HttpServletResponse.SC_OK, cached.status);
            assertEquals("text/plain", cached.contentType);
            assertEquals(List.of("orders"), cached.headers.get("X-Page"));
            assertEquals(1, cached.cookies.size());
            assertEquals("view", cached.cookies.get(0).getName());
            assertEquals("summary", cached.cookies.get(0).getValue());
            assertEquals(cached.body.size(), cached.contentLength);
            assertTrue(cached.isCommitted());
        } finally {
            filter.destroy();
            manager.shutdown();
        }
    }

    private static final class OrderPageFilter extends CachingFilter {
        private final CacheManager manager;

        private OrderPageFilter(CacheManager manager) {
            this.manager = manager;
        }

        @Override
        public void doInit() {
            super.doInit();
            blockingCache.setTimeoutMillis(10000);
        }

        @Override
        protected String getCacheName() {
            return "pages";
        }

        @Override
        protected CacheManager getCacheManager() {
            return manager;
        }

        @Override
        protected String calculateKey(HttpServletRequest request) {
            return request.getRequestURI() + "?" + request.getQueryString();
        }
    }
}
