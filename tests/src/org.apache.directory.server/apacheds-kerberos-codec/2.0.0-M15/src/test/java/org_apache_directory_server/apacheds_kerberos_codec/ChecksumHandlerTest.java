/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_directory_server.apacheds_kerberos_codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.directory.server.kerberos.shared.crypto.checksum.ChecksumHandler;
import org.apache.directory.shared.kerberos.components.Checksum;
import org.apache.directory.shared.kerberos.crypto.checksum.ChecksumType;
import org.apache.directory.shared.kerberos.exceptions.KerberosException;
import org.junit.jupiter.api.Test;

public class ChecksumHandlerTest {
    @Test
    void calculatesAndVerifiesRsaMd5Checksum() throws Exception {
        ChecksumHandler handler = new ChecksumHandler();
        byte[] data = "Apache Directory Kerberos checksum".getBytes("UTF-8");

        Checksum checksum = handler.calculateChecksum(ChecksumType.RSA_MD5, data, null, null);

        assertThat(checksum.getChecksumType()).isEqualTo(ChecksumType.RSA_MD5);
        assertThat(checksum.getChecksumValue()).containsExactly(
                (byte) 0x30, (byte) 0x1e, (byte) 0x33, (byte) 0xbc,
                (byte) 0xe2, (byte) 0xdb, (byte) 0xc3, (byte) 0x2f,
                (byte) 0x05, (byte) 0x85, (byte) 0x98, (byte) 0x92,
                (byte) 0x49, (byte) 0x02, (byte) 0x72, (byte) 0x2e);
        assertThatCode(() -> handler.verifyChecksum(checksum, data, null, null)).doesNotThrowAnyException();
    }

    @Test
    void rejectsMismatchedRsaMd5Checksum() throws Exception {
        ChecksumHandler handler = new ChecksumHandler();
        byte[] data = "Apache Directory Kerberos checksum".getBytes("UTF-8");
        Checksum checksum = handler.calculateChecksum(ChecksumType.RSA_MD5, data, null, null);
        byte[] alteredChecksum = checksum.getChecksumValue().clone();
        alteredChecksum[0] ^= 0x01;

        assertThatThrownBy(() -> handler.verifyChecksum(new Checksum(ChecksumType.RSA_MD5, alteredChecksum), data, null, null))
                .isInstanceOf(KerberosException.class);
    }
}
