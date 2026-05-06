/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcutil_jdk15to18;

import java.math.BigInteger;

import org.bouncycastle.asn1.ASN1Enumerated;
import org.bouncycastle.oer.OEROptional;
import org.bouncycastle.oer.its.etsi102941.AuthorizationResponseCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

public class OEROptionalAnonymous1Test {
    @Test
    public void getObjectUsesTargetTypeGetInstance() {
        BigInteger expectedValue = BigInteger.valueOf(3L);
        ASN1Enumerated encodedValue = new ASN1Enumerated(expectedValue);
        OEROptional optional = OEROptional.getInstance(encodedValue);

        AuthorizationResponseCode decodedValue = optional.getObject(AuthorizationResponseCode.class);

        assertThat(decodedValue).isNotSameAs(encodedValue);
        assertThat(decodedValue.getValue()).isEqualTo(expectedValue);
    }

    @Test
    public void getObjectWrapsGetInstanceFailures() {
        OEROptional optional = OEROptional.getInstance(new ASN1Enumerated(BigInteger.valueOf(27L)));

        assertThatIllegalStateException()
                .isThrownBy(() -> optional.getObject(AuthorizationResponseCode.class))
                .withMessageContaining("could not invoke getInstance on type");
    }
}
