/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import org.postgresql.ssl.BouncyCastlePrivateKeyFactory;

import java.security.PrivateKey;
import java.security.Provider;
import java.util.Base64;

import javax.security.auth.callback.PasswordCallback;

import static org.assertj.core.api.Assertions.assertThat;

public class BouncyCastlePrivateKeyFactoryTest {
    private static final char[] PASSWORD = "changeit".toCharArray();
    private static final byte[] PLAIN_PRIVATE_KEY = Base64.getMimeDecoder().decode("""
            MIICdgIBADANBgkqhkiG9w0BAQEFAASCAmAwggJcAgEAAoGBAMlFeiQtD7nq5zBoxjTiXIk61hjk
            Ya3M1RP9OifcfWSJ+ilNkVTyGgNsttmR98G7ogVsbTVH3XfWTJbDfopfy6YFMEqoL/XjXa4Q7u2M
            8qlnCnnWdIank8wi40WOo59Vkm8ITPQb5Avyb1qJ+WfkyCI6DWs4P8PnQ3QfENj2LckDAgMBAAEC
            gYEAoHDjxoatLJUWKb67kViIT1Q5aqpTOwo3KLIJc9ENm+FpKxeMLFy5Y9V7hMAY8bQymYQvIfPh
            lS+c6el4q7FlUc3bvtA4WvClPzOikBX5RLC6O/C/OzFmoryCIjTB7j8ma1Iv+MEdDOZsX/GEEUqN
            o8mDQ5SYECk4YBIw15/gAeECQQDx8XwvsFOTGqfzMw9IKi5VUL3mqhrXS6ZGj2xaubBG1DF1DBu4
            68/G7ncdrouzxDJbMLKdbRB5K3841B4B3gEZAkEA1PcQMXznX4AwmOD7+LRla3vyvw6BfUMjntqw
            zPLY7rYxJShPVJpgCuGKHvnTRRoYJ6pPwl63smGSbjcdkFuSewJAPGOQIb8bPS58GUH3YIXWxsi+
            faKbzH0/ZYFLBGIw050HMzXwfXmuhPLknG4CxL4F66j+DDk60WO//4lCbCjjkQJAT3vWo2HGyTIC
            8UwnPu+1WqRsOTqkwaepR8S/b/7DUHTXatLcqOrlJap/7oe3fnb6xPMfyZ+oV/9qs5AGjLZguQJA
            NqPDS/toadH1bQ/G11cp7fEUmOZvHxClCldnzykiwYf/ZGql0/BZ+Izs6Nng2ssv0JHq0n91kXJx
            OeY6Yxbw8w==
            """);
    private static final byte[] ENCRYPTED_PRIVATE_KEY = Base64.getMimeDecoder().decode("""
            MIICojAcBgoqhkiG9w0BDAEDMA4ECJfdZ5ud5/GpAgIIAASCAoAPTt2GTguWKeSbJ/lMLrHvPOWf
            KHyg9ajWBA8iVrON3C2jPsUf6h5z/z2gLYCuHavpikgIpyMvJuI/NqJJJhepMdiw7NpKfLyYYLAc
            WT8XpP0ga9pF0SvMscBH392XwA7Tj1uXiz1tfHTpz5ZUf+WzGpaYPkyCoCrz8EWm7AU5uCaa/88V
            9LodCi4kmTX9NFaL5o+7lgmJXIZdcoifI0SxOcI2nlvYZehpp9bv7aKwaxrsZG3zoG4o82p6TQoF
            0l2T5aGCHBXXr0Uu+/wLOhY816jL2XrE8+qlkVYaJ6e5JwvVvIO1wz9Ptm4OXSwT8d1poJh9vHIO
            DY5ZOtfaCagikEOZaN0CXX80lrqzemzl+vTs/+0rduztrHiPOfYN5pvpMRgcTnp174TsJwpgRJWF
            xVSU22eT5T0yowNPks44cop++dYpSW/FnVFSaPmiNZtxKgc8Fd4v+ekJSMPuabGhCLmWvfZaAMkY
            2i2n3xY/m2PAMVXy1jCqtLeCkJ+e53VrJifK2wuN+ucc+3rqFSSiifjoms228ws6ZsCx9/WY0sb/
            GGe0DEtxjCQqj9onsfoA+S3EJQfMoJWdjMlyY0XbYpoS3mE399NAT3+B0zeyCaDLLJsWude8XL8J
            /u40wOIH2R1tb0R1IIgloTn5oM2EDGCWwOe3vYAHob16ev7r1rwPVhmPwBxBv1JCeCdMe1Nn1VXL
            Bgk74rHBO68QCxoCTiPS2oeKNRo75yghsLYE5c7s9FnCtme1ohy6TpZk39rHeZKAf4sSdOC04pqo
            UEawrhkkP80ESeRShSZnaazdQqOoTr079SqY7IGB1OzePMMji2Td1K/5yUV1WTe/cIDw
            """);

    @Test
    void initializesBouncyCastleProvider() throws Exception {
        Provider provider = BouncyCastlePrivateKeyFactory.initBouncyCastleProvider();

        assertThat(provider).isInstanceOf(BouncyCastleProvider.class);
        assertThat(provider.getName()).isEqualTo("BC");
    }

    @Test
    void decryptsEncryptedPkcs8PrivateKeyWithBouncyCastle() throws Exception {
        PasswordCallback passwordCallback = new PasswordCallback("Enter SSL password: ", false);
        passwordCallback.setPassword(PASSWORD);

        PrivateKey decryptedPrivateKey = new BouncyCastlePrivateKeyFactory()
                .getPrivateKeyFromEncryptedKey(ENCRYPTED_PRIVATE_KEY, passwordCallback);

        assertThat(decryptedPrivateKey.getAlgorithm()).isEqualTo("RSA");
        assertThat(decryptedPrivateKey.getEncoded()).containsExactly(PLAIN_PRIVATE_KEY);
    }
}
