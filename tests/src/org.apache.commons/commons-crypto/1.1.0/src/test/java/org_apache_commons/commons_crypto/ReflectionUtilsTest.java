/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.apache.commons.crypto.Crypto;
import org.apache.commons.crypto.random.CryptoRandom;
import org.apache.commons.crypto.random.CryptoRandomFactory;
import org.apache.commons.crypto.utils.ReflectionUtils;
import org.junit.jupiter.api.Test;

public class ReflectionUtilsTest {
    @Test
    void getClassByNameLoadsRequestedCommonsCryptoClass() throws Exception {
        final Class<?> cryptoClass = ReflectionUtils.getClassByName(Crypto.class.getName());

        assertThat(cryptoClass).isSameAs(Crypto.class);
    }

    @Test
    void newInstanceCreatesObjectWithNoArgumentConstructor() {
        final CryptoRandomFactory factory = ReflectionUtils.newInstance(CryptoRandomFactory.class);

        assertThat(factory).isInstanceOf(CryptoRandomFactory.class);
    }

    @Test
    void cryptoRandomFactoryCreatesJavaProviderThroughReflectionUtils() throws Exception {
        final Properties properties = new Properties();
        properties.setProperty(CryptoRandomFactory.CLASSES_KEY, CryptoRandomFactory.RandomProvider.JAVA.getClassName());
        final byte[] randomBytes = new byte[16];

        try (CryptoRandom random = CryptoRandomFactory.getCryptoRandom(properties)) {
            assertThat(random).isNotNull();
            random.nextBytes(randomBytes);
        }
    }
}
