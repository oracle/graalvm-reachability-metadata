/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bouncycastle.bcutil_jdk15on;

import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.cmc.BodyPartID;
import org.bouncycastle.oer.OEROptional;
import org.bouncycastle.oer.its.EndEntityType;
import org.bouncycastle.oer.its.PsidGroupPermissions;
import org.bouncycastle.oer.its.SubjectPermissions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OEROptionalAnonymous1Test {
    @Test
    void convertsDefinedValueThroughGetInstanceFactory() {
        ASN1Integer encodedBodyPartId = new ASN1Integer(42L);
        OEROptional optionalValue = OEROptional.getInstance(encodedBodyPartId);

        BodyPartID resolvedValue = optionalValue.getObject(BodyPartID.class);

        assertThat(resolvedValue.getID()).isEqualTo(42L);
    }

    @Test
    void parsesDefinedSequenceDefaultsThroughOptionalFactory() {
        SubjectPermissions subjectPermissions = SubjectPermissions.builder()
            .all()
            .createSubjectPermissions();
        PsidGroupPermissions permissions = PsidGroupPermissions.builder()
            .setSubjectPermissions(subjectPermissions)
            .setMinChainLength(1L)
            .setChainLengthRange(2L)
            .setEeType(new EndEntityType(EndEntityType.app))
            .createPsidGroupPermissions();

        PsidGroupPermissions parsedPermissions = PsidGroupPermissions.getInstance(
            permissions.toASN1Primitive());

        ASN1Integer minChainLength = parsedPermissions.getMinChainLength();
        ASN1Integer chainLengthRange = parsedPermissions.getChainLengthRange();
        assertThat(minChainLength.getValue().longValueExact()).isEqualTo(1L);
        assertThat(chainLengthRange.getValue().longValueExact()).isEqualTo(2L);
        assertThat(parsedPermissions.getEeType()).isNotNull();
    }
}
