/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_opengauss.opengauss_jdbc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.postgresql.ssl.LazyKeyManager;
import org.postgresql.ssl.PrivateKeyFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.util.Arrays;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises dynamic access paths owned by {@code org.postgresql.ssl.LazyKeyManager}.
 */
public class LazyKeyManagerTest {
    private static final char[] PASSWORD = "changeit".toCharArray();
    private static final byte[] INVALID_PRIVATE_KEY = "not-a-pkcs8-private-key".getBytes(StandardCharsets.UTF_8);
    private static final String CERTIFICATE = """
            -----BEGIN CERTIFICATE-----
            MIICBDCCAW2gAwIBAgIUOAnYXxt/6Hz5eZZwW8m6vAPFO+QwDQYJKoZIhvcNAQEL
            BQAwFDESMBAGA1UEAwwJbG9jYWxob3N0MB4XDTI2MDUwMTE0MjYwN1oXDTI2MDUw
            MjE0MjYwN1owFDESMBAGA1UEAwwJbG9jYWxob3N0MIGfMA0GCSqGSIb3DQEBAQUA
            A4GNADCBiQKBgQCxpJBC0cl9e+JfiVWi3SPIi5fZIJRM5mdLeBnH44nEwsi605F+
            Mc8W/33fTMQZhhnPgs9TwYWHtltGzv0YjdV+sYLDAVVI1g/iEzpP48wEpLvVcq6T
            O+syUAzoWzLSXl1zPJC/ptbJtgZcYE2BQ44PPtjU7GlIKwucegEKPN+v9wIDAQAB
            o1MwUTAdBgNVHQ4EFgQU8ugWQazhRbh+h8tJ3OBB6+IX69cwHwYDVR0jBBgwFoAU
            8ugWQazhRbh+h8tJ3OBB6+IX69cwDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0B
            AQsFAAOBgQCQfQUcw6IhPlnVIGO9Xb9eTMI6Z6XDSQCF3dyiSSozqrGizrMYYvUf
            KLizuyDo9SqkSUNHyd2KXwDkUtHYC2fRl+nDDZz9Eo97HhZxiNuUZx82mlY+RWdI
            4tfQaByHeZPZnzs6BVwOUojWsg1HXHCrdqZ34rt5nY2AoPGgBZa+Zg==
            -----END CERTIFICATE-----
            """;

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesInvalidPkcs8KeyWithConfiguredPrivateKeyFactory() throws Exception {
        TestPrivateKeyFactory.reset();
        Path certificateFile = temporaryDirectory.resolve("client.crt");
        Path keyFile = temporaryDirectory.resolve("client.pk8");
        Files.writeString(certificateFile, CERTIFICATE);
        Files.write(keyFile, INVALID_PRIVATE_KEY);
        CallbackHandler callbackHandler = new PasswordCallbackHandler(PASSWORD);
        LazyKeyManager keyManager = new LazyKeyManager(certificateFile.toString(), keyFile.toString(),
                callbackHandler, false, TestPrivateKeyFactory.class.getName());

        PrivateKey privateKey = keyManager.getPrivateKey("user");

        keyManager.throwKeyManagerException();
        assertThat(privateKey).isSameAs(TestPrivateKeyFactory.returnedPrivateKey());
        assertThat(TestPrivateKeyFactory.lastKeyData()).containsExactly(INVALID_PRIVATE_KEY);
        assertThat(TestPrivateKeyFactory.lastPassword()).containsExactly(PASSWORD);
    }

    private static final class PasswordCallbackHandler implements CallbackHandler {
        private final char[] password;

        private PasswordCallbackHandler(char[] password) {
            this.password = Arrays.copyOf(password, password.length);
        }

        @Override
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            for (Callback callback : callbacks) {
                if (callback instanceof PasswordCallback passwordCallback) {
                    passwordCallback.setPassword(password);
                } else {
                    throw new UnsupportedCallbackException(callback);
                }
            }
        }
    }

    public static final class TestPrivateKeyFactory implements PrivateKeyFactory {
        private static final PrivateKey RETURNED_PRIVATE_KEY = new TestPrivateKey();
        private static byte[] lastKeyData;
        private static char[] lastPassword;

        public TestPrivateKeyFactory() {
        }

        @Override
        public PrivateKey getPrivateKeyFromEncryptedKey(byte[] data, PasswordCallback pwdcb) {
            lastKeyData = Arrays.copyOf(data, data.length);
            lastPassword = Arrays.copyOf(pwdcb.getPassword(), pwdcb.getPassword().length);
            return RETURNED_PRIVATE_KEY;
        }

        private static void reset() {
            lastKeyData = null;
            lastPassword = null;
        }

        private static PrivateKey returnedPrivateKey() {
            return RETURNED_PRIVATE_KEY;
        }

        private static byte[] lastKeyData() {
            return Arrays.copyOf(lastKeyData, lastKeyData.length);
        }

        private static char[] lastPassword() {
            return Arrays.copyOf(lastPassword, lastPassword.length);
        }
    }

    private static final class TestPrivateKey implements PrivateKey {
        private static final long serialVersionUID = 1L;

        @Override
        public String getAlgorithm() {
            return "RSA";
        }

        @Override
        public String getFormat() {
            return "PKCS#8";
        }

        @Override
        public byte[] getEncoded() {
            return new byte[] {1, 2, 3};
        }
    }
}
