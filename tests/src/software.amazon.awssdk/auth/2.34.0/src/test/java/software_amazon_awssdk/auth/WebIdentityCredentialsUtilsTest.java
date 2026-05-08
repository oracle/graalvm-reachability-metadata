/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenCredentialsProviderFactory;
import software.amazon.awssdk.auth.credentials.internal.WebIdentityCredentialsUtils;

public class WebIdentityCredentialsUtilsTest {
    @Test
    void factoryCreatesStsWebIdentityCredentialsProviderFactory() {
        WebIdentityTokenCredentialsProviderFactory factory = WebIdentityCredentialsUtils.factory();

        assertThat(factory.getClass().getName()).contains("StsWebIdentityCredentialsProviderFactory");
    }
}
