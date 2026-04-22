/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import com.sun.jna.platform.win32.DdemlUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DdemlUtil_StandaloneDdeClientTest {
    @Test
    void constructorCreatesStandaloneClientBeforeInitialization() {
        DdemlUtil.StandaloneDdeClient client = new DdemlUtil.StandaloneDdeClient();

        assertThat(client).isNotNull();
        assertThat(client.getInstanceIdentitifier()).isNull();
    }
}
