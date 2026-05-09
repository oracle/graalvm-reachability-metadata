/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jets3t.jets3t;

import org.jets3t.service.security.AWSCredentials;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AWSCredentialsTest {
    @Test
    public void storesAccessSecretAndFriendlyName() {
        AWSCredentials credentials = new AWSCredentials("access-key", "secret-key", "coverage user");

        assertThat(credentials.getAccessKey()).isEqualTo("access-key");
        assertThat(credentials.getSecretKey()).isEqualTo("secret-key");
        assertThat(credentials.getFriendlyName()).isEqualTo("coverage user");
    }
}
