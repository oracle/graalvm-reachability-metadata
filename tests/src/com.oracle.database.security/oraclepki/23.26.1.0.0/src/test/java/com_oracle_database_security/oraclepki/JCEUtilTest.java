/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_security.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.Provider;
import java.security.Security;
import oracle.security.pki.JCEUtil;
import org.junit.jupiter.api.Test;

public class JCEUtilTest {
    @Test
    void loadsConfiguredJceProviderByClassName() throws Exception {
        Security.setProperty(
                JCEUtil.JCE_FIPS140_PROVIDER_CLASS_SECURITY_PROPERTY,
                "oracle.security.pki.OraclePKIProvider");
        JCEUtil.clearCachedObjects();

        Provider provider = JCEUtil.getJCEProvider();

        assertThat(provider.getName()).isEqualTo("OraclePKI");
    }
}
