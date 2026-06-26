/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sun.jna.platform.win32.DdemlUtil.IDdeClient;
import com.sun.jna.platform.win32.DdemlUtil.StandaloneDdeClient;

public class DdemlUtilInnerStandaloneDdeClientTest {
    @Test
    void createsStandaloneClientWithoutInitializingDdeSession() {
        StandaloneDdeClient client = new StandaloneDdeClient();

        assertThat(client).isInstanceOf(IDdeClient.class);
        assertThat(client.getInstanceIdentitifier()).isNull();
    }
}
