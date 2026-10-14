/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_oauth_client.google_oauth_client;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.client.auth.oauth.OAuthHmacSha256Signer;
import org.junit.jupiter.api.Test;

public class OAuthHmacSha256SignerTest {
    @Test
    void signsOAuthSignatureBaseStringWithEscapedSecrets() throws Exception {
        OAuthHmacSha256Signer signer = new OAuthHmacSha256Signer("client secret");
        signer.setTokenSecret("token/secret");

        String signature =
                signer.computeSignature(
                        "POST&https%3A%2F%2Fapi.example%2Fresource&"
                                + "a%3Dfirst%26b%3Dsecond");

        assertThat(signer.getSignatureMethod()).isEqualTo("HMAC-SHA256");
        assertThat(signature).isEqualTo("3kYdD1AttWtm9BkO1Gx78yDdFt53cI3ol6AFTYjGhSM=");
    }
}
