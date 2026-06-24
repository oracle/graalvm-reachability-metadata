/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_archiver;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;

import org.codehaus.plexus.archiver.util.ArchiveEntryUtils;
import org.codehaus.plexus.components.io.attributes.Java7Reflector;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.codehaus.plexus.util.Os;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sun.misc.Unsafe;

import static org.assertj.core.api.Assertions.assertThat;

public class ArchiveEntryUtilsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void chmodWithJvmPermissionsAppliesAllFilePermissionMethods() throws Exception {
        File file = temporaryDirectory.resolve("archive-entry-utils-permissions.txt").toFile();
        assertThat(file.createNewFile()).isTrue();

        boolean originalJvmFilePermAvailable = ArchiveEntryUtils.jvmFilePermAvailable;
        ArchiveEntryUtils.jvmFilePermAvailable = true;
        try (Java7DetectionRestorer ignored = Java7DetectionRestorer.forceJava6PermissionsPath()) {
            Logger logger = new ConsoleLogger(Logger.LEVEL_DISABLED, "archive-entry-utils-test");
            int ownerReadWriteExecuteMode = 0700;

            ArchiveEntryUtils.chmod(file, ownerReadWriteExecuteMode, logger, true);

            assertThat(file)
                .isFile()
                .canRead()
                .canWrite();
            if (Os.isFamily(Os.FAMILY_UNIX)) {
                assertThat(file.canExecute()).isTrue();
            }
        } finally {
            ArchiveEntryUtils.jvmFilePermAvailable = originalJvmFilePermAvailable;
        }
    }

    private static final class Java7DetectionRestorer implements AutoCloseable {
        private final Object staticFieldBase;
        private final long staticFieldOffset;
        private final boolean originalValue;

        private Java7DetectionRestorer(Object staticFieldBase, long staticFieldOffset, boolean originalValue) {
            this.staticFieldBase = staticFieldBase;
            this.staticFieldOffset = staticFieldOffset;
            this.originalValue = originalValue;
        }

        private static Java7DetectionRestorer forceJava6PermissionsPath() throws ReflectiveOperationException {
            Java7Reflector.isJava7();
            Unsafe unsafe = getUnsafe();
            Field java7Field = Java7Reflector.class.getDeclaredField("isJava7");
            Object staticFieldBase = unsafe.staticFieldBase(java7Field);
            long staticFieldOffset = unsafe.staticFieldOffset(java7Field);
            boolean originalValue = unsafe.getBooleanVolatile(staticFieldBase, staticFieldOffset);
            unsafe.putBooleanVolatile(staticFieldBase, staticFieldOffset, false);
            return new Java7DetectionRestorer(staticFieldBase, staticFieldOffset, originalValue);
        }

        @Override
        public void close() throws ReflectiveOperationException {
            getUnsafe().putBooleanVolatile(staticFieldBase, staticFieldOffset, originalValue);
        }
    }

    private static Unsafe getUnsafe() throws ReflectiveOperationException {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (Unsafe) unsafeField.get(null);
    }
}
