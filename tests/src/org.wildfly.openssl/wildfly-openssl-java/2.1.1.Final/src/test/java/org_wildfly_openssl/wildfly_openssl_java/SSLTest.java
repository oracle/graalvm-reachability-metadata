/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_wildfly_openssl.wildfly_openssl_java;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.wildfly.openssl.SSL;

import static org.assertj.core.api.Assertions.assertThat;

public class SSLTest {
    @Test
    void getInstanceUsesPackagedNativeLibraryFallback(@TempDir Path openSslDirectory) {
        String originalOpenSslPath = System.getProperty(SSL.ORG_WILDFLY_OPENSSL_PATH);
        String originalWfsslPath = System.getProperty(SSL.ORG_WILDFLY_LIBWFSSL_PATH);
        try {
            System.setProperty(SSL.ORG_WILDFLY_OPENSSL_PATH, openSslDirectory.toString());
            System.clearProperty(SSL.ORG_WILDFLY_LIBWFSSL_PATH);

            try {
                SSL ssl = SSL.getInstance();

                assertThat(ssl).isNotNull();
            } catch (RuntimeException exception) {
                assertThat(hasInvocationTargetExceptionCause(exception)).isFalse();
            } catch (Error error) {
                if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                    throw error;
                }
            }
        } finally {
            setOrClearProperty(SSL.ORG_WILDFLY_OPENSSL_PATH, originalOpenSslPath);
            setOrClearProperty(SSL.ORG_WILDFLY_LIBWFSSL_PATH, originalWfsslPath);
        }
    }

    private static boolean hasInvocationTargetExceptionCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof InvocationTargetException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static void setOrClearProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
