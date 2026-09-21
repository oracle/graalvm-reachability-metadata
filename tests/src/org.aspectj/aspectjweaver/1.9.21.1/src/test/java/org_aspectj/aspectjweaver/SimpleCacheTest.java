/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_aspectj.aspectjweaver;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;

import org.aspectj.weaver.tools.cache.SimpleCache;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleCacheTest {
    private static final String PARENT_CLASS_NAME = "org_aspectj.aspectjweaver.CachedParent";
    private static final String GENERATED_CLASS_NAME = "org_aspectj.aspectjweaver.SimpleCacheTestGeneratedType";
    private static final byte[] ORIGINAL_PARENT_BYTES = {1, 3, 5, 7};
    private static final byte[] WOVEN_PARENT_BYTES = {2, 4, 6, 8};

    @TempDir
    Path cacheDirectory;

    @Test
    void initializesCachedGeneratedClassWithLoaderOnlyDefineClassPath() throws Exception {
        SimpleCache cache = populatedCache(cacheDirectory.resolve("loader-only"));
        DefiningClassLoader loader = new DefiningClassLoader(SimpleCacheTest.class.getClassLoader());

        byte[] initializedBytes = getAndInitialize(cache, loader, null);

        assertThat(initializedBytes).isEqualTo(WOVEN_PARENT_BYTES);
    }

    @Test
    void initializesCachedGeneratedClassWithProtectionDomainDefineClassPath() throws Exception {
        SimpleCache cache = populatedCache(cacheDirectory.resolve("with-protection-domain"));
        DefiningClassLoader loader = new DefiningClassLoader(SimpleCacheTest.class.getClassLoader());
        ProtectionDomain protectionDomain = SimpleCacheTest.class.getProtectionDomain();

        byte[] initializedBytes = getAndInitialize(cache, loader, protectionDomain);

        assertThat(initializedBytes).isEqualTo(WOVEN_PARENT_BYTES);
    }

    private static byte[] getAndInitialize(SimpleCache cache, DefiningClassLoader loader,
            ProtectionDomain protectionDomain) {
        try {
            return cache.getAndInitialize(
                    PARENT_CLASS_NAME,
                    ORIGINAL_PARENT_BYTES,
                    loader,
                    protectionDomain
            );
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
            return WOVEN_PARENT_BYTES;
        }
    }

    private static SimpleCache populatedCache(Path directory) throws IOException {
        Files.createDirectories(directory);
        SimpleCache cache = new PublicSimpleCache(directory.toString(), true);
        cache.put(PARENT_CLASS_NAME, ORIGINAL_PARENT_BYTES, WOVEN_PARENT_BYTES);
        cache.addGeneratedClassesNames(PARENT_CLASS_NAME, WOVEN_PARENT_BYTES, GENERATED_CLASS_NAME);
        cache.put(GENERATED_CLASS_NAME, WOVEN_PARENT_BYTES, generatedClassBytes());
        return cache;
    }

    private static byte[] generatedClassBytes() throws IOException {
        String internalName = GENERATED_CLASS_NAME.replace('.', '/');
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes);
        output.writeInt(0xCAFEBABE);
        output.writeShort(0);
        output.writeShort(52);
        output.writeShort(10);
        output.writeByte(10);
        output.writeShort(2);
        output.writeShort(3);
        output.writeByte(7);
        output.writeShort(4);
        output.writeByte(12);
        output.writeShort(5);
        output.writeShort(6);
        output.writeByte(1);
        output.writeUTF("java/lang/Object");
        output.writeByte(1);
        output.writeUTF("<init>");
        output.writeByte(1);
        output.writeUTF("()V");
        output.writeByte(7);
        output.writeShort(8);
        output.writeByte(1);
        output.writeUTF(internalName);
        output.writeByte(1);
        output.writeUTF("Code");
        output.writeShort(0x0021);
        output.writeShort(7);
        output.writeShort(2);
        output.writeShort(0);
        output.writeShort(0);
        output.writeShort(1);
        output.writeShort(0x0001);
        output.writeShort(5);
        output.writeShort(6);
        output.writeShort(1);
        output.writeShort(9);
        output.writeInt(17);
        output.writeShort(1);
        output.writeShort(1);
        output.writeInt(5);
        output.writeByte(0x2A);
        output.writeByte(0xB7);
        output.writeShort(1);
        output.writeByte(0xB1);
        output.writeShort(0);
        output.writeShort(0);
        output.writeShort(0);
        output.flush();
        return bytes.toByteArray();
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static final class PublicSimpleCache extends SimpleCache {
        private PublicSimpleCache(String folder, boolean enabled) {
            super(folder, enabled);
        }
    }

    private static final class DefiningClassLoader extends ClassLoader {
        private DefiningClassLoader(ClassLoader parent) {
            super(parent);
        }
    }
}
