/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.util.Collections;
import java.util.Enumeration;

import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletContext;
import org.apache.catalina.filters.RateLimitFilter;
import org.apache.catalina.util.RateLimiter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RateLimitFilterTest {

    @Test
    void createsAndConfiguresSelectedRateLimiter() throws Exception {
        RecordingRateLimiter.created = null;
        RateLimitFilter filter = new RateLimitFilter();
        filter.setRateLimitClassName(RecordingRateLimiter.class.getName());
        filter.setBucketDuration(30);
        filter.setBucketRequests(12);
        filter.setPolicyName("api");

        filter.init(filterConfig());

        assertThat(RecordingRateLimiter.created).isNotNull();
        assertThat(RecordingRateLimiter.created.getDuration()).isEqualTo(30);
        assertThat(RecordingRateLimiter.created.getRequests()).isEqualTo(12);
        assertThat(RecordingRateLimiter.created.getPolicy()).isEqualTo("\"api\";q=12;w=30");
    }

    private static FilterConfig filterConfig() {
        return new FilterConfig() {
            @Override
            public String getFilterName() {
                return "rate-limit";
            }

            @Override
            public ServletContext getServletContext() {
                return null;
            }

            @Override
            public String getInitParameter(String name) {
                return null;
            }

            @Override
            public Enumeration<String> getInitParameterNames() {
                return Collections.emptyEnumeration();
            }
        };
    }

    public static final class RecordingRateLimiter implements RateLimiter {
        private static RecordingRateLimiter created;
        private int duration;
        private int requests;
        private String policyName;

        public RecordingRateLimiter() {
            created = this;
        }

        @Override
        public int getDuration() {
            return duration;
        }

        @Override
        public void setDuration(int duration) {
            this.duration = duration;
        }

        @Override
        public int getRequests() {
            return requests;
        }

        @Override
        public void setRequests(int requests) {
            this.requests = requests;
        }

        @Override
        public int increment(String identifier) {
            return 1;
        }

        @Override
        public void destroy() {
        }

        @Override
        public void setFilterConfig(FilterConfig filterConfig) {
        }

        @Override
        public String getPolicyName() {
            return policyName;
        }

        @Override
        public void setPolicyName(String name) {
            policyName = name;
        }
    }
}
