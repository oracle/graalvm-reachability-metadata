/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_rest_assured.rest_assured;

import io.restassured.internal.TrustAndKeystoreSpecImpl;
import io.restassured.internal.TrustAndKeystoreSpecImplCreateStoreClosure1DirectAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TrustAndKeystoreSpecImplInner_createStore_closure1Test {
    private static final String DYNAMIC_RESOLUTION_TARGET =
            "io.restassured.internal.TrustAndKeystoreSpecImpl$_createStore_closure1";
    private static final String STORE_PASSWORD = "changeit";

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatedAccessClassReachesCompilerGeneratedClassResolver() throws Throwable {
        Class<?> resolvedClass = TrustAndKeystoreSpecImplCreateStoreClosure1DirectAccess
                .resolveWithCompilerGeneratedClassResolver(DYNAMIC_RESOLUTION_TARGET);

        assertEquals(DYNAMIC_RESOLUTION_TARGET, resolvedClass.getName());
    }

    @Test
    void createStoreLoadsFileThroughWithInputStreamClosure() throws Exception {
        String keyStoreType = KeyStore.getDefaultType();
        Path keyStoreFile = temporaryDirectory.resolve("client-keystore." + keyStoreType);
        KeyStore expectedKeyStore = KeyStore.getInstance(keyStoreType);
        expectedKeyStore.load(null, STORE_PASSWORD.toCharArray());
        try (OutputStream outputStream = Files.newOutputStream(keyStoreFile)) {
            expectedKeyStore.store(outputStream, STORE_PASSWORD.toCharArray());
        }
        TrustAndKeystoreSpecImpl specification = new TrustAndKeystoreSpecImpl();

        KeyStore loadedKeyStore = specification.createStore(keyStoreType, keyStoreFile.toFile(), STORE_PASSWORD);

        assertNotNull(loadedKeyStore);
        assertEquals(keyStoreType, loadedKeyStore.getType());
        assertEquals(expectedKeyStore.size(), loadedKeyStore.size());
    }
}
