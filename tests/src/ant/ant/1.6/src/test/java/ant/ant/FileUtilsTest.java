/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.tools.ant.util.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

public class FileUtilsTest {
    private static final long EXPECTED_LAST_MODIFIED_TIME = 1_600_000_000_000L;

    @TempDir
    Path tempDirectory;

    @Test
    void setsFileLastModifiedThroughPublicUtilityMethod() throws Throwable {
        Path file = Files.createFile(tempDirectory.resolve("timestamped-file.txt"));
        File targetFile = file.toFile();

        ExposedFileUtils.clearCachedSetLastModifiedMethod();
        ExposedFileUtils.clearCachedFileClass();
        assertThat(ExposedFileUtils.lookupCompilerGeneratedClass(File.class.getName()))
                .isSameAs(File.class);
        ExposedFileUtils.clearCachedFileClass();
        FileUtils.newFileUtils().setFileLastModified(targetFile, EXPECTED_LAST_MODIFIED_TIME);

        assertThat(targetFile.lastModified())
                .isCloseTo(
                        EXPECTED_LAST_MODIFIED_TIME,
                        offset(FileUtils.FAT_FILE_TIMESTAMP_GRANULARITY));
    }

    private static final class ExposedFileUtils {
        private static final MethodHandle CLASS_LOOKUP = classLookupMethod();
        private static final VarHandle SET_LAST_MODIFIED_METHOD = staticField(
                "setLastModified",
                Method.class);
        private static final VarHandle FILE_CLASS = staticField(
                "class$java$io$File",
                Class.class);

        static Class<?> lookupCompilerGeneratedClass(String className) throws Throwable {
            return (Class<?>) CLASS_LOOKUP.invoke(className);
        }

        static void clearCachedSetLastModifiedMethod() {
            SET_LAST_MODIFIED_METHOD.set(null);
        }

        static void clearCachedFileClass() {
            FILE_CLASS.set(null);
        }

        private static MethodHandle classLookupMethod() {
            try {
                return MethodHandles.privateLookupIn(FileUtils.class, MethodHandles.lookup())
                        .findStatic(
                                FileUtils.class,
                                "class$",
                                MethodType.methodType(Class.class, String.class));
            } catch (NoSuchMethodException | IllegalAccessException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }

        private static VarHandle staticField(String fieldName, Class<?> fieldType) {
            try {
                return MethodHandles.privateLookupIn(FileUtils.class, MethodHandles.lookup())
                        .findStaticVarHandle(FileUtils.class, fieldName, fieldType);
            } catch (NoSuchFieldException | IllegalAccessException exception) {
                throw new ExceptionInInitializerError(exception);
            }
        }
    }
}
