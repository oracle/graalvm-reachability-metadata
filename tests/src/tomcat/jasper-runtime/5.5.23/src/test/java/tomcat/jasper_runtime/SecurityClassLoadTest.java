/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat.jasper_runtime;

import org.apache.jasper.security.SecurityClassLoad;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityClassLoadTest {
    @Test
    void securityClassLoadInitializesRuntimeSecuritySupport() {
        assertThat(System.getSecurityManager()).isNull();

        SecurityClassLoad.securityClassLoad(Thread.currentThread().getContextClassLoader());
    }
}
