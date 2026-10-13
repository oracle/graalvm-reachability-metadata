/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_bytedeco.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.bytedeco.ffmpeg.global.avutil.av_aes_alloc;
import static org.bytedeco.ffmpeg.global.avutil.av_aes_crypt;
import static org.bytedeco.ffmpeg.global.avutil.av_aes_init;
import static org.bytedeco.ffmpeg.global.avutil.av_free;

import org.bytedeco.ffmpeg.avutil.AVAES;
import org.junit.jupiter.api.Test;

public class AVAESTest {
    @Test
    void encryptsAndDecryptsAes128Block() {
        byte[] key = {
            0x00, 0x01, 0x02, 0x03,
            0x04, 0x05, 0x06, 0x07,
            0x08, 0x09, 0x0a, 0x0b,
            0x0c, 0x0d, 0x0e, 0x0f
        };
        byte[] plaintext = {
            0x00, 0x11, 0x22, 0x33,
            0x44, 0x55, 0x66, 0x77,
            (byte) 0x88, (byte) 0x99, (byte) 0xaa, (byte) 0xbb,
            (byte) 0xcc, (byte) 0xdd, (byte) 0xee, (byte) 0xff
        };
        byte[] expectedCiphertext = {
            0x69, (byte) 0xc4, (byte) 0xe0, (byte) 0xd8,
            0x6a, 0x7b, 0x04, 0x30,
            (byte) 0xd8, (byte) 0xcd, (byte) 0xb7, (byte) 0x80,
            0x70, (byte) 0xb4, (byte) 0xc5, 0x5a
        };
        byte[] ciphertext = new byte[plaintext.length];
        byte[] decrypted = new byte[plaintext.length];
        AVAES context = av_aes_alloc();

        try {
            assertThat(context).isNotNull();
            assertThat(context.isNull()).isFalse();

            assertThat(av_aes_init(context, key, 128, 0)).isZero();
            av_aes_crypt(context, ciphertext, plaintext, 1, (byte[]) null, 0);
            assertThat(ciphertext).containsExactly(expectedCiphertext);

            assertThat(av_aes_init(context, key, 128, 1)).isZero();
            av_aes_crypt(context, decrypted, ciphertext, 1, (byte[]) null, 1);
            assertThat(decrypted).containsExactly(plaintext);
        } finally {
            if (context != null && !context.isNull()) {
                av_free(context);
            }
        }
    }
}
