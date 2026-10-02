/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_ws.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import javax.xml.namespace.QName;

import com.sun.xml.ws.policy.AssertionSet;
import com.sun.xml.ws.policy.Policy;
import org.junit.jupiter.api.Test;

public class PolicyTest {
    @Test
    void createsPoliciesThroughPublicFactories() {
        Policy empty = Policy.createEmptyPolicy("empty", "empty-policy");
        Policy nullPolicy = Policy.createNullPolicy("null", "null-policy");
        Policy explicit = Policy.createPolicy(List.of(AssertionSet.emptyAssertionSet()));

        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.isNull()).isFalse();
        assertThat(nullPolicy.isNull()).isTrue();
        assertThat(explicit.getNumberOfAssertionSets()).isEqualTo(1);
    }

    @Test
    void exposesPolicyIdentityAndAlternativeContents() {
        Policy policy =
                Policy.createPolicy(
                        "service-policy", "service-id", List.of(AssertionSet.emptyAssertionSet()));

        assertThat(policy.getName()).isEqualTo("service-policy");
        assertThat(policy.getId()).isEqualTo("service-id");
        assertThat(policy.getIdOrName()).isEqualTo("service-id");
        assertThat(policy.getVocabulary()).isEmpty();
        assertThat(policy.iterator().next().isEmpty()).isTrue();
    }

    @Test
    void comparesEquivalentPoliciesByValue() {
        Policy first =
                Policy.createPolicy(
                        "service-policy", "service-id", List.of(AssertionSet.emptyAssertionSet()));
        Policy equivalent =
                Policy.createPolicy(
                        "service-policy", "service-id", List.of(AssertionSet.emptyAssertionSet()));

        assertThat(first).isEqualTo(equivalent);
        assertThat(first.hashCode()).isEqualTo(equivalent.hashCode());
    }

    @Test
    void queriesPolicyVocabularyThroughPublicApi() {
        QName assertionName = new QName("urn:shipping", "DeliveryMode");
        AssertionSet alternative = AssertionSet.emptyAssertionSet();
        Policy policy = Policy.createPolicy("shipping-policy", null, List.of(alternative));

        assertThat(policy.getIdOrName()).isEqualTo("shipping-policy");
        assertThat(policy.contains(assertionName)).isFalse();
        assertThat(policy.contains(assertionName.getNamespaceURI())).isFalse();
        assertThat(alternative.contains(assertionName)).isFalse();
        assertThat(alternative.get(assertionName)).isEmpty();
    }
}
