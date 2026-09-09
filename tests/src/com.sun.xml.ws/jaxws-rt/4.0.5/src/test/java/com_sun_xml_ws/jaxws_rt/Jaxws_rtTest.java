/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_xml_ws.jaxws_rt;

import com.oracle.webservices.api.databinding.ExternalMetadataFeature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Jaxws_rtTest {
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
