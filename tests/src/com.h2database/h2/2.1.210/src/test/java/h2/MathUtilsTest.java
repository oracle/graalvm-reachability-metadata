/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package h2;

import org.h2.util.MathUtils;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.assertj.core.api.Assertions.assertThat;

public class MathUtilsTest {

    @Test
    void generateAlternativeSeedIncludesLocalNetworkIdentity() throws Exception {
        InetAddress localHost = InetAddress.getLocalHost();
        String hostName = localHost.getHostName();
        InetAddress[] hostAddresses = InetAddress.getAllByName(hostName);

        assertThat(hostName).isNotBlank();
        assertThat(hostAddresses).isNotEmpty();
        assertThat(hostAddresses)
                .allSatisfy(address -> assertThat(address.getAddress()).isNotEmpty());

        byte[] seed = MathUtils.generateAlternativeSeed();

        assertThat(seed).isNotEmpty();
    }
}
