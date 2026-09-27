/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_ws.rt;

import com.oracle.webservices.api.EnvelopeStyle;
import com.oracle.webservices.api.EnvelopeStyleFeature;
import com.oracle.webservices.api.databinding.DatabindingFactory;
import com.oracle.webservices.api.databinding.ExternalMetadataFeature;
import com.sun.xml.ws.api.BindingID;
import com.sun.xml.ws.api.SOAPVersion;
import jakarta.xml.ws.soap.SOAPBinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(60)
public class RtTest {
    @Test
    void discoversAndConfiguresTheRuntimeDatabindingFactory() {
        DatabindingFactory factory = DatabindingFactory.newInstance();

        assertThat(factory).isNotNull();
        assertThat(factory.properties()).isEmpty();

        factory.properties().put("com.example.databinding.mode", "strict");
        assertThat(factory.properties()).containsEntry("com.example.databinding.mode", "strict");
    }

    @Test
    void convertsBetweenSoapVersionsAndEnvelopeStyles() {
        EnvelopeStyleFeature soap11Feature = SOAPVersion.SOAP_11.toFeature();
        EnvelopeStyleFeature soap12Feature = SOAPVersion.SOAP_12.toFeature();

        assertThat(soap11Feature.getStyles()).containsExactly(EnvelopeStyle.Style.SOAP11);
        assertThat(soap12Feature.getStyles()).containsExactly(EnvelopeStyle.Style.SOAP12);
        assertThat(SOAPVersion.from(soap11Feature)).isEqualTo(SOAPVersion.SOAP_11);
        assertThat(SOAPVersion.from(soap12Feature)).isEqualTo(SOAPVersion.SOAP_12);
        assertThat(SOAPVersion.fromHttpBinding(SOAPVersion.SOAP_12.httpBindingId))
                .isEqualTo(SOAPVersion.SOAP_12);
        assertThat(SOAPVersion.fromNsUri(SOAPVersion.SOAP_11.nsUri)).isEqualTo(SOAPVersion.SOAP_11);
    }

    @Test
    void parsesStandardSoapBindingIdentifiers() {
        BindingID soap11 = BindingID.parse(SOAPBinding.SOAP11HTTP_BINDING);
        BindingID soap12Mtom = BindingID.parse(SOAPBinding.SOAP12HTTP_MTOM_BINDING);

        assertThat(soap11.getSOAPVersion()).isEqualTo(SOAPVersion.SOAP_11);
        assertThat(soap11.canGenerateWSDL()).isTrue();
        assertThat(soap11.toString()).isEqualTo(SOAPBinding.SOAP11HTTP_BINDING);
        assertThat(soap12Mtom.getSOAPVersion()).isEqualTo(SOAPVersion.SOAP_12);
        assertThat(soap12Mtom.toString()).isEqualTo(SOAPBinding.SOAP12HTTP_MTOM_BINDING);
    }

    @Test
    void retainsExternalMetadataConfiguration() {
        ExternalMetadataFeature feature = ExternalMetadataFeature.builder()
                .addResources("META-INF/ws-bindings.xml", "META-INF/ws-policy.xml")
                .setEnabled(false)
                .build();

        assertThat(feature.isEnabled()).isFalse();
        assertThat(feature.getResourceNames())
                .containsExactly("META-INF/ws-bindings.xml", "META-INF/ws-policy.xml");
    }
}
