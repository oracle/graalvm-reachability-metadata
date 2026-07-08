/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.signer.SignerLoader;
import software.amazon.awssdk.core.signer.Signer;

@SuppressWarnings("deprecation")
public class SignerLoaderTest {
    @Test
    void sigV4aSignerIsLoadedFromAuthCrtModule() {
        Signer signer = SignerLoader.getSigV4aSigner();

        assertThat(signer.getClass().getName())
                .isEqualTo("software.amazon.awssdk.authcrt.signer.internal.DefaultAwsCrtV4aSigner");
    }
}
