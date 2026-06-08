/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_poi.poi_ooxml;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Provider;

import org.apache.poi.poifs.crypt.dsig.SignatureConfig;
import org.junit.jupiter.api.Test;

public class SignatureConfigTest {
    private static final String SANTUARIO_XML_DSIG_PROVIDER =
            "org.apache.jcp.xml.dsig.internal.dom.XMLDSigRI";

    @Test
    public void resolvesConfiguredJsr105Provider() {
        String previousProvider = System.getProperty("jsr105Provider");
        try {
            System.setProperty("jsr105Provider", SANTUARIO_XML_DSIG_PROVIDER);

            SignatureConfig signatureConfig = new SignatureConfig();
            Provider provider = signatureConfig.getProvider();

            assertThat(provider.getClass().getName()).isEqualTo(SANTUARIO_XML_DSIG_PROVIDER);
            assertThat(provider.getServices()).isNotEmpty();
        } finally {
            if (previousProvider == null) {
                System.clearProperty("jsr105Provider");
            } else {
                System.setProperty("jsr105Provider", previousProvider);
            }
        }
    }
}
