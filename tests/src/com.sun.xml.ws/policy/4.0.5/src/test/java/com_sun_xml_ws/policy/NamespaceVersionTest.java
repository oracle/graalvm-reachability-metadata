/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_ws.policy;

import static org.assertj.core.api.Assertions.assertThat;

import javax.xml.namespace.QName;

import com.sun.xml.ws.policy.Policy;
import com.sun.xml.ws.policy.sourcemodel.wspolicy.NamespaceVersion;
import com.sun.xml.ws.policy.sourcemodel.wspolicy.XmlToken;
import org.junit.jupiter.api.Test;

public class NamespaceVersionTest {
    @Test
    void resolvesPolicyTokensAcrossSupportedNamespaceVersions() {
        QName policyV12 = NamespaceVersion.v1_2.asQName(XmlToken.Policy);
        QName policyV15 = NamespaceVersion.v1_5.asQName(XmlToken.Policy);

        assertThat(policyV12.getNamespaceURI()).isEqualTo(NamespaceVersion.v1_2.toString());
        assertThat(policyV15.getNamespaceURI()).isEqualTo(NamespaceVersion.v1_5.toString());
        assertThat(NamespaceVersion.resolveVersion(policyV12)).isEqualTo(NamespaceVersion.v1_2);
        assertThat(NamespaceVersion.resolveVersion(policyV15)).isEqualTo(NamespaceVersion.v1_5);
        assertThat(NamespaceVersion.resolveVersion(policyV12.getNamespaceURI())).isEqualTo(NamespaceVersion.v1_2);
        assertThat(NamespaceVersion.resolveVersion(policyV15.getNamespaceURI())).isEqualTo(NamespaceVersion.v1_5);
        assertThat(NamespaceVersion.resolveAsToken(policyV12)).isEqualTo(XmlToken.Policy);
        assertThat(NamespaceVersion.resolveAsToken(policyV15)).isEqualTo(XmlToken.Policy);
        assertThat(NamespaceVersion.v1_2.getDefaultNamespacePrefix()).isEqualTo("wsp1_2");
        assertThat(NamespaceVersion.v1_5.getDefaultNamespacePrefix()).isEqualTo("wsp");
    }

    @Test
    void createsPoliciesWithAnExplicitNamespaceVersion() {
        Policy emptyPolicy = Policy.createEmptyPolicy(
                NamespaceVersion.v1_2, "EmptyPolicy", "empty-id");
        Policy nullPolicy = Policy.createNullPolicy(
                NamespaceVersion.v1_5, "NullPolicy", "null-id");

        assertThat(emptyPolicy.getNamespaceVersion()).isEqualTo(NamespaceVersion.v1_2);
        assertThat(emptyPolicy.isEmpty()).isTrue();
        assertThat(emptyPolicy.getId()).isEqualTo("empty-id");
        assertThat(emptyPolicy.getName()).isEqualTo("EmptyPolicy");
        assertThat(nullPolicy.getNamespaceVersion()).isEqualTo(NamespaceVersion.v1_5);
        assertThat(nullPolicy.isNull()).isTrue();
        assertThat(nullPolicy.getId()).isEqualTo("null-id");
        assertThat(nullPolicy.getName()).isEqualTo("NullPolicy");
    }
}
