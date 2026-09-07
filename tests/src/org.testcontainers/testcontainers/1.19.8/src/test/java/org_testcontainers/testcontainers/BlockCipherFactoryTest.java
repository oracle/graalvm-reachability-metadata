/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_testcontainers.testcontainers;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.trilead.ssh2.crypto.cipher.BlockCipher;
import org.testcontainers.shaded.com.trilead.ssh2.crypto.cipher.BlockCipherFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class BlockCipherFactoryTest {
    @Test
    void createsAndUsesTheConfiguredSshBlockCipher() {
        byte[] key = new byte[BlockCipherFactory.getKeySize("aes128-ctr")];
        byte[] initializationVector = new byte[BlockCipherFactory.getBlockSize("aes128-ctr")];
        BlockCipher cipher = BlockCipherFactory.createCipher("aes128-ctr", true, key, initializationVector);
        byte[] encrypted = new byte[cipher.getBlockSize()];

        cipher.transformBlock(new byte[cipher.getBlockSize()], 0, encrypted, 0);

        assertThat(encrypted).hasSize(cipher.getBlockSize()).isNotEqualTo(new byte[cipher.getBlockSize()]);
    }
}
