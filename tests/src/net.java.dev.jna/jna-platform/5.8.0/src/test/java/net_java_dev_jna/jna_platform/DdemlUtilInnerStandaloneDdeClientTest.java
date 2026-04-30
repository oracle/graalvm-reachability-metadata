/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna_platform;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.jna.platform.win32.DdemlUtil.IDdeClient;
import com.sun.jna.platform.win32.DdemlUtil.StandaloneDdeClient;
import java.io.Closeable;
import org.junit.jupiter.api.Test;

public class DdemlUtilInnerStandaloneDdeClientTest {
    @Test
    void constructorCreatesMessageLoopBackedClientProxy() {
        StandaloneDdeClient client = new StandaloneDdeClient();

        assertThat(client).isInstanceOf(IDdeClient.class);
        assertThat(client).isInstanceOf(Closeable.class);
        assertThat(client.getInstanceIdentitifier()).isNull();
    }
}
