/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents_core5.httpcore5;

import org.apache.hc.core5.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilsTest {
    @Test
    void callSetterAndGetterAccessJavaBeanProperties() {
        final MutableConnectionConfig config = new MutableConnectionConfig();

        ReflectionUtils.callSetter(config, "TimeoutMillis", int.class, 5000);
        ReflectionUtils.callSetter(config, "Secure", boolean.class, true);

        assertThat(ReflectionUtils.callGetter(config, "TimeoutMillis", Integer.class)).isEqualTo(5000);
        assertThat(ReflectionUtils.callGetter(config, "Secure", Boolean.class)).isTrue();
    }

    public static final class MutableConnectionConfig {
        private int timeoutMillis;
        private boolean secure;

        public int getTimeoutMillis() {
            return timeoutMillis;
        }

        public void setTimeoutMillis(final int timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
        }

        public Boolean getSecure() {
            return secure;
        }

        public void setSecure(final boolean secure) {
            this.secure = secure;
        }
    }
}
