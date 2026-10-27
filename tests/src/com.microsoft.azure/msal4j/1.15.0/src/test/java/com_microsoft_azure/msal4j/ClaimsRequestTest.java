/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_microsoft_azure.msal4j;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.aad.msal4j.ClaimsRequest;
import com.microsoft.aad.msal4j.RequestedClaimAdditionalInfo;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class ClaimsRequestTest {
    @Test
    void formatsAndParsesIdTokenClaims() {
        ClaimsRequest claimsRequest = new ClaimsRequest();
        claimsRequest.requestClaimInIdToken(
                "groups", new RequestedClaimAdditionalInfo(true, null, List.of("engineering", "operations")));
        claimsRequest.requestClaimInIdToken("email", null);

        String formattedClaims = claimsRequest.formatAsJSONString();

        assertThat(formattedClaims)
                .isEqualTo("{\"id_token\":{\"groups\":{\"essential\":true,"
                        + "\"values\":[\"engineering\",\"operations\"]},\"email\":null}}");

        ClaimsRequest parsedClaimsRequest = ClaimsRequest.formatAsClaimsRequest(formattedClaims);
        assertThat(parsedClaimsRequest.getIdTokenRequestedClaims())
                .extracting(claim -> claim.name)
                .containsExactly("groups", "email");

        assertThat(parsedClaimsRequest.formatAsJSONString()).isEqualTo(formattedClaims);
    }
}
