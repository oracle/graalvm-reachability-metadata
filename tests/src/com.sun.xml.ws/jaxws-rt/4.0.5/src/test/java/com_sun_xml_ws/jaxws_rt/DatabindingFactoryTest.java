/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_ws.jaxws_rt;

import com.oracle.webservices.api.EnvelopeStyle;
import com.oracle.webservices.api.EnvelopeStyleFeature;
import com.oracle.webservices.api.databinding.DatabindingFactory;
import com.oracle.webservices.api.databinding.ExternalMetadataFeature;
import com.sun.xml.ws.api.SOAPVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DatabindingFactoryTest {
    @Test
    void createsAConfigurableFactoryThroughThePublicEntryPoint() {
        DatabindingFactory factory = DatabindingFactory.newInstance();

        assertThat(factory).isNotNull();
        assertThat(factory.properties()).isEmpty();

        factory.properties().put("com.example.transport", "in-memory");
        assertThat(factory.properties()).containsEntry("com.example.transport", "in-memory");
    }

    @Test
    void soapVersionConvertsEnvelopeStyleFeature() {
        EnvelopeStyleFeature feature = SOAPVersion.SOAP_12.toFeature();

        assertThat(feature.getStyles()).containsExactly(EnvelopeStyle.Style.SOAP12);
        assertThat(SOAPVersion.from(feature)).isEqualTo(SOAPVersion.SOAP_12);
        assertThat(SOAPVersion.fromHttpBinding(SOAPVersion.SOAP_12.httpBindingId))
                .isEqualTo(SOAPVersion.SOAP_12);
        assertThat(SOAPVersion.fromNsUri(SOAPVersion.SOAP_12.nsUri)).isEqualTo(SOAPVersion.SOAP_12);
    }

    @Test
    void externalMetadataFeatureRetainsConfiguredResourceNames() {
        ExternalMetadataFeature feature = ExternalMetadataFeature.builder()
                .addResources("META-INF/ws-metadata.xml", "META-INF/ws-policy.xml")
                .setEnabled(false)
                .build();

        assertThat(feature.isEnabled()).isFalse();
        assertThat(feature.getResourceNames())
                .containsExactly("META-INF/ws-metadata.xml", "META-INF/ws-policy.xml");
    }
}
