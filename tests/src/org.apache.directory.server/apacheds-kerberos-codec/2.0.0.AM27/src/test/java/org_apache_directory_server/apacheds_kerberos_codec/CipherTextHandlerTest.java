/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_directory_server.apacheds_kerberos_codec;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;

import org.apache.directory.server.kerberos.shared.crypto.encryption.KerberosKeyFactory;
import org.apache.directory.shared.kerberos.codec.KerberosDecoder;
import org.apache.directory.shared.kerberos.codec.types.EncryptionType;
import org.apache.directory.shared.kerberos.components.EncryptionKey;
import org.junit.jupiter.api.Test;

public class CipherTextHandlerTest {
    @Test
    void derivesAndRoundTripsAesKey() throws Exception {
        EncryptionKey key = KerberosKeyFactory.string2Key(
                "service/localhost@EXAMPLE.COM",
                "correct horse battery staple",
                EncryptionType.AES128_CTS_HMAC_SHA1_96);
        int encodedLength = key.computeLength();
        ByteBuffer encoded = key.encode(ByteBuffer.allocate(encodedLength));

        EncryptionKey decoded = KerberosDecoder.decodeEncryptionKey(encoded.array());

        assertThat(key.getKeyType()).isEqualTo(EncryptionType.AES128_CTS_HMAC_SHA1_96);
        assertThat(key.getKeyValue()).isNotEmpty();
        assertThat(decoded.getKeyType()).isEqualTo(key.getKeyType());
        assertThat(decoded.getKeyValue()).containsExactly(key.getKeyValue());
    }
}
