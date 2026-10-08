/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_sshd.sshd_common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.security.Security;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.sshd.common.util.security.SecurityEntityFactory;
import org.junit.jupiter.api.Test;

public class SecurityEntityFactoryAnonymous3Test {
    @Test
    void createsACipherUsingAProviderInstance() throws Exception {
        Provider provider = Security.getProvider("SunJCE");
        assertThat(provider).isNotNull();

        SecurityEntityFactory<Cipher> factory =
                SecurityEntityFactory.toProviderInstanceFactory(Cipher.class, provider);
        SecretKeySpec key = new SecretKeySpec(new byte[16], "AES");
        IvParameterSpec iv = new IvParameterSpec(new byte[16]);
        byte[] payload = "sshd provider cipher".getBytes(StandardCharsets.UTF_8);

        Cipher encryptor = factory.getInstance("AES/CBC/PKCS5Padding");
        encryptor.init(Cipher.ENCRYPT_MODE, key, iv);
        byte[] encrypted = encryptor.doFinal(payload);

        Cipher decryptor = factory.getInstance("AES/CBC/PKCS5Padding");
        decryptor.init(Cipher.DECRYPT_MODE, key, iv);

        assertThat(factory.getEntityType()).isEqualTo(Cipher.class);
        assertThat(encryptor.getProvider()).isSameAs(provider);
        assertThat(decryptor.getProvider()).isSameAs(provider);
        assertThat(encrypted).isNotEqualTo(payload);
        assertThat(decryptor.doFinal(encrypted)).isEqualTo(payload);
    }
}
