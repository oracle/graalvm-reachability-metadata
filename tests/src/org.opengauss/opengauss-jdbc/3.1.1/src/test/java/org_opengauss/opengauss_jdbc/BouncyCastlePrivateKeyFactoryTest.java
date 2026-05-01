/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.junit.jupiter.api.Test;
import org.postgresql.ssl.BouncyCastlePrivateKeyFactory;

import java.security.PrivateKey;
import java.security.Provider;
import java.util.Base64;

import javax.security.auth.callback.PasswordCallback;

import static org.assertj.core.api.Assertions.assertThat;

public class BouncyCastlePrivateKeyFactoryTest {
    private static final char[] PASSWORD = "changeit".toCharArray();
    private static final String ENCRYPTED_PKCS8_PRIVATE_KEY = """
            MIIC3TBXBgkqhkiG9w0BBQ0wSjApBgkqhkiG9w0BBQwwHAQI+T4Qq0lZyHYCAggAMAwGCCqGSIb3
            DQIJBQAwHQYJYIZIAWUDBAEqBBDzXGIn8BD0mmhyEfYRcUHxBIICgJvRo4hzppN6XcFlQHsVn5PT
            JE3DaFysUfbqaOkKUHtoKhdGD4zXoUe0uVGWQFh098lg/26p3tV+x7teeWEHJ1kMZyLzZQXsY5pi
            jUe/t7plLre/bmAgip5ktxLx6IugON9HYXAhUVK1hTQjK4QU0qnss0RUPyDpZv75YvnOpVn7sjzv
            mRZs6uB5asrC6SvyxAmCTz5cF1IxGAyTBm2j1HMXIIsw3L11uMjXQztPUL6PJouh+IV8Z0PRdJh9
            D1gd8Fa164yQO2TKpsE/T2RThl53Nunc3Lo3/99Tqn5MYlxGGa+d1RzQ5im64rplxt/3xOqb/uO7
            YMM8kdd3EslmRLnZBGfklaJ2sH2VXMjvvWOCbp5Dr3oNih+VqDGWDMmsmUHdJjBT5szVdI3cC4nz
            N0SdB10mLzed7g3e/87OpMrtJ5CZ3UpFfB7d8BqMBt+G3ZWsVid2Yrg/w3vM2zN5anth61LZzMVS
            JuRc+x4bVdup8ruO8T4/kBUQXGKSXSL32RKMxaD+tWjBmUbE747kMpSECKrLAZuaFRLyXXMg4iHG
            dE0Iq0Kkqn+MejedKKHka9/t3XuWybtDuO8xSVpBeKQG8KiZUqgbkxOJC2FsROaCo9hYj4BH0FJt
            KZ1/ZgFGG7JHxa9u4VNDGCtMl0GKs7VyQVC+B7M+sSfeFfvapiGzW32KxR1+A9EpSo0z3ZdvIjik
            z9G9/RaObFX19cUl53eFeZ6ADd9/zl3O8Tp3j64K4TKQYjft8JgLYg/2XkjGEE9nxDqgf92PlwAa
            0XkE4Qa46uo61blDZ+cEpc24EVGdz44Tcrvl93SLDirzFSPbbzBZrPtzSLoPkeC3h27AHrE=
            """;

    @Test
    void createsBouncyCastleProvider() throws Exception {
        Provider provider = BouncyCastlePrivateKeyFactory.initBouncyCastleProvider();

        assertThat(provider).isNotNull();
        assertThat(provider.getName()).isEqualTo("BC");
    }

    @Test
    void decryptsOpenSslPkcs8PrivateKeyWithBouncyCastle() throws Exception {
        PasswordCallback passwordCallback = new PasswordCallback("PKCS#8 password", false);
        passwordCallback.setPassword(PASSWORD);

        PrivateKey privateKey = new BouncyCastlePrivateKeyFactory().getPrivateKeyFromEncryptedKey(
                Base64.getMimeDecoder().decode(ENCRYPTED_PKCS8_PRIVATE_KEY),
                passwordCallback);

        assertThat(privateKey).isNotNull();
        assertThat(privateKey.getAlgorithm()).isEqualTo("RSA");
        assertThat(privateKey.getEncoded()).isNotEmpty();
    }
}
