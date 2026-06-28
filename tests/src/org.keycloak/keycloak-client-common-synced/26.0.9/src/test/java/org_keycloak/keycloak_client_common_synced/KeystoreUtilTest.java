/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_keycloak.keycloak_client_common_synced;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyStore;

import org.junit.jupiter.api.Test;
import org.keycloak.common.constants.GenericConstants;
import org.keycloak.common.util.KeystoreUtil;

public class KeystoreUtilTest {
    private static final String KEYSTORE_RESOURCE = "keystores/client-common-test-keystore.p12";
    private static final String KEYSTORE_PASSWORD = "changeit";

    @Test
    void loadsClasspathKeystoreWithContextClassLoader() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(KeystoreUtilTest.class.getClassLoader());
        try {
            KeyStore keyStore = KeystoreUtil.loadKeyStore(
                    GenericConstants.PROTOCOL_CLASSPATH + KEYSTORE_RESOURCE,
                    KEYSTORE_PASSWORD);

            assertThat(keyStore.containsAlias("test")).isTrue();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void loadsClasspathKeystoreWithKeystoreUtilClassFallback() throws Exception {
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(null);
        try {
            KeyStore keyStore = KeystoreUtil.loadKeyStore(
                    GenericConstants.PROTOCOL_CLASSPATH + "/" + KEYSTORE_RESOURCE,
                    KEYSTORE_PASSWORD);

            assertThat(keyStore.containsAlias("test")).isTrue();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }
}
