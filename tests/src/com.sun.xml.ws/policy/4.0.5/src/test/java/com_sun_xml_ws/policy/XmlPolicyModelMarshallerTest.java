/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_ws.policy;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.xml.ws.policy.Policy;
import com.sun.xml.ws.policy.PolicyAssertion;
import com.sun.xml.ws.policy.sourcemodel.PolicyModelMarshaller;
import com.sun.xml.ws.policy.sourcemodel.PolicyModelTranslator;
import com.sun.xml.ws.policy.sourcemodel.PolicyModelUnmarshaller;
import com.sun.xml.ws.policy.sourcemodel.PolicySourceModel;
import java.io.StringReader;
import java.io.StringWriter;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import org.junit.jupiter.api.Test;

public class XmlPolicyModelMarshallerTest {
    private static final String POLICY_NAMESPACE = "http://www.w3.org/ns/ws-policy";
    private static final String WSU_NAMESPACE =
            "http://docs.oasis-open.org/wss/2004/01/"
                    + "oasis-200401-wss-wssecurity-utility-1.0.xsd";
    private static final QName TRANSPORT_ASSERTION =
            new QName("urn:example:transport", "Transport");

    @Test
    void roundTripsAndTranslatesPolicyXmlThroughPublicModelApis() throws Exception {
        String policyXml =
                """
                <wsp:Policy xmlns:wsp="http://www.w3.org/ns/ws-policy"
                            xmlns:wsu="%s"
                            xmlns:t="urn:example:transport"
                            wsu:Id="transport-policy" wsp:Name="secure-transport">
                    <wsp:ExactlyOne>
                        <wsp:All>
                            <t:Transport wsp:Optional="true" mode="secure">tls</t:Transport>
                        </wsp:All>
                    </wsp:ExactlyOne>
                </wsp:Policy>
                """
                        .formatted(WSU_NAMESPACE);

        PolicySourceModel sourceModel =
                PolicyModelUnmarshaller.getXmlUnmarshaller()
                        .unmarshalModel(new StringReader(policyXml));
        StringWriter output = new StringWriter();
        XMLStreamWriter xmlWriter = XMLOutputFactory.newFactory().createXMLStreamWriter(output);

        PolicyModelMarshaller.getXmlMarshaller(false).marshal(sourceModel, xmlWriter);
        xmlWriter.close();

        String marshalledPolicy = output.toString();
        assertThat(marshalledPolicy)
                .contains(
                        POLICY_NAMESPACE,
                        "transport-policy",
                        "secure-transport",
                        "Transport",
                        "tls");

        PolicySourceModel roundTrippedModel =
                PolicyModelUnmarshaller.getXmlUnmarshaller()
                        .unmarshalModel(new StringReader(marshalledPolicy));
        Policy policy = PolicyModelTranslator.getTranslator().translate(roundTrippedModel);
        PolicyAssertion assertion = policy.iterator().next().iterator().next();

        assertThat(policy.getId()).isEqualTo("transport-policy");
        assertThat(policy.getName()).isEqualTo("secure-transport");
        assertThat(policy.getNumberOfAssertionSets()).isEqualTo(1);
        assertThat(assertion.getName()).isEqualTo(TRANSPORT_ASSERTION);
        assertThat(assertion.getValue()).isEqualTo("tls");
        assertThat(assertion.isOptional()).isTrue();
        assertThat(assertion.getAttributeValue(new QName("mode"))).isEqualTo("secure");
    }
}
