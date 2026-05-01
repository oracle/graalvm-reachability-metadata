/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_mwiede.jsch;

import static org.assertj.core.api.Assertions.assertThat;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class KeyPairPKCS8Test {
    private static final byte[] PASSPHRASE =
            "correct-horse-battery-staple".getBytes(StandardCharsets.UTF_8);

    @Test
    void decryptsEncryptedPkcs8PrivateKeyWithPbes2Pbkdf2AndAesCbc() throws JSchException {
        KeyPair keyPair = KeyPair.load(new JSch(), encryptedPkcs8PrivateKey(), null);
        try {
            assertThat(keyPair.isEncrypted()).isTrue();
            assertThat(keyPair.decrypt(PASSPHRASE)).isTrue();
            assertThat(keyPair.isEncrypted()).isFalse();
            assertThat(keyPair.getKeyType()).isEqualTo(KeyPair.RSA);
            assertThat(keyPair.getFingerPrint()).isNotBlank();
        } finally {
            keyPair.dispose();
        }
    }

    private static byte[] encryptedPkcs8PrivateKey() {
        String pem = """
                -----BEGIN ENCRYPTED PRIVATE KEY-----
                MIIC3TBXBgkqhkiG9w0BBQ0wSjApBgkqhkiG9w0BBQwwHAQIsHLxwebHwEoCAgPo
                MAwGCCqGSIb3DQIJBQAwHQYJYIZIAWUDBAEqBBDSJO+aG0jyQZJpK8JkopZrBIIC
                gGjSQ9l1Wi4NLv54478uqh8lY8fWpg5fueHKG6AhkfavFw310NZ+dq2c3D89XMUK
                4BZ3gHQ/wstoNHOaRZ9J2pmDmjwqgwK3uCSwOsLL8TK0AS6LSPkhBjF8WWZsyMXi
                8qCjkTzHJGm1RTCPbvGq2/7JN6hG60qjb4cw3fMr4PFQK9n4UIzjF24aADkUlv3s
                AIZnrRzDsQIOWkqbNL+VR43AjVxfQFt3Mn2yhI6AkiNMfl6pLuLMMBK1tn4e4xAY
                /ViH9YUBsC8ivhQXodySQhAERl+Om4iB3tdP+VWKcfWJd+HM9Jpjb2+HgIWCd1JZ
                Q95w924NBb8qt5ggxrZa4Ez+i+ClH68yfPQzgzKaXitx9zSjKLByh2Tp03IU4cbV
                c1BptKGS96nPzyDV8m5XFDkHMglROxHgn2o+1us1QrFkw+6tR1mSC99UEAOpCGXU
                nmZcGrdyIjC3tNxBH7pO/XstBBY8SWreLE8lbdcv0/us/2FCsqqNFmP+N+Xwot24
                82QWaPSwPoTyCY2I4b3oYAcz8mJ4xwVze0An7mmpYMbtm2HLqZ0HXljLujRvOTCf
                QVSd+zJSC/X9wrKD9TOwuQAoUHnE3SPJ0GCtCvf5gTg9jvcMMVaQVsvDlqYMdD4P
                Rhj9NlAhShEZxTCe3Ab35o58QVPEDAlmaNfvHXA4yC7tEeiZGu1MIncC67S9BdiR
                aSoWVVvqlWsA6dex1+/3Sczid4SGraPTpIrmsqoj4QYlYValoaEVGzBtMJRSeUNc
                7rUPkakIF3UeAXeHFmOmDtv5YFzBDHgVxOl8Nl+aSTt5rPzSmY8GqQ8s3EfEwCzd
                tsicgNwa7ABk5jHQVESqGCs=
                -----END ENCRYPTED PRIVATE KEY-----
                """;
        return pem.getBytes(StandardCharsets.US_ASCII);
    }
}
