/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_poi.poi_ooxml;

import java.security.Provider;

import org.apache.poi.poifs.crypt.dsig.SignatureConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SignatureConfigTest {
    private static final String JSR_105_PROVIDER_PROPERTY = "jsr105Provider";

    @Test
    void getProviderInstantiatesConfiguredJsr105Provider() {
        String previousProvider = System.getProperty(JSR_105_PROVIDER_PROPERTY);
        System.setProperty(JSR_105_PROVIDER_PROPERTY, TestXmlDsigProvider.class.getName());
        try {
            SignatureConfig config = new SignatureConfig();

            Provider provider = config.getProvider();

            assertThat(provider).isInstanceOf(TestXmlDsigProvider.class);
            assertThat(provider.getName()).isEqualTo("signature-config-test-provider");
        } finally {
            restoreProviderProperty(previousProvider);
        }
    }

    private static void restoreProviderProperty(String previousProvider) {
        if (previousProvider == null) {
            System.clearProperty(JSR_105_PROVIDER_PROPERTY);
        } else {
            System.setProperty(JSR_105_PROVIDER_PROPERTY, previousProvider);
        }
    }

    public static final class TestXmlDsigProvider extends Provider {
        private static final long serialVersionUID = 1L;

        public TestXmlDsigProvider() {
            super("signature-config-test-provider", "1", "Provider used by SignatureConfig tests");
        }
    }
}
