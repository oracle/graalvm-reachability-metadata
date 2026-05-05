/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kerby.kerb_core;

import org.apache.kerby.asn1.type.Asn1OctetString;
import org.apache.kerby.kerberos.kerb.type.ad.AuthorizationDataEntry;
import org.apache.kerby.kerberos.kerb.type.ad.AuthorizationType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class AuthorizationDataEntryTest {
    @Test
    void decodesAuthorizationDataAsRequestedAsn1Type() throws Exception {
        byte[] payload = "delegated-authz".getBytes(StandardCharsets.UTF_8);
        Asn1OctetString encodedData = new Asn1OctetString(payload);
        AuthorizationDataEntry entry = new AuthorizationDataEntry(
                AuthorizationType.AD_IF_RELEVANT,
                encodedData.encode());

        Asn1OctetString decodedData = entry.getAuthzDataAs(Asn1OctetString.class);

        assertThat(decodedData).isNotNull();
        assertThat(decodedData.getValue()).isEqualTo(payload);
    }
}
