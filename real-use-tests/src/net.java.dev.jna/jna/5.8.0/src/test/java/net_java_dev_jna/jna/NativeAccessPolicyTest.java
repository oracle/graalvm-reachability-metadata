/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_java_dev_jna.jna;

import com.sun.jna.DefaultTypeMapper;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.ToNativeContext;
import com.sun.jna.ToNativeConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises JNA's custom type mapping to apply application access policies through native chmod.
 */
public class NativeAccessPolicyTest {

    @Test
    @Timeout(60)
    public void sharesAnExportWithItsGroupAndThenRevokesGroupAccess(@TempDir Path directory) throws IOException {
        DefaultTypeMapper mapper = new DefaultTypeMapper();
        mapper.addToNativeConverter(AccessPolicy.class, new AccessPolicyConverter());
        NativePermissions permissions = Native.load(Platform.C_LIBRARY_NAME, NativePermissions.class,
                Map.of(Library.OPTION_TYPE_MAPPER, mapper));
        Path export = directory.resolve("accounts.csv");
        String content = "account,balance\nACCT-17,12500\n";
        Files.writeString(export, content);

        assertEquals(0, permissions.chmod(export.toString(), AccessPolicy.OWNER_ONLY),
                "The export must initially be restricted to its owner");
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(export));

        assertEquals(0, permissions.chmod(export.toString(), AccessPolicy.GROUP_READABLE),
                "Sharing must grant the group read access without write or execute access");
        assertEquals(PosixFilePermissions.fromString("rw-r-----"), Files.getPosixFilePermissions(export));
        assertEquals(content, Files.readString(export), "Changing access must preserve the export contents");

        assertEquals(0, permissions.chmod(export.toString(), AccessPolicy.OWNER_ONLY));
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(export),
                "Revoking sharing must remove group access rather than retain an earlier converted value");
        assertEquals(content, Files.readString(export));
    }

    public interface NativePermissions extends Library {
        int chmod(String path, AccessPolicy policy);
    }

    public enum AccessPolicy {
        OWNER_ONLY(0600),
        GROUP_READABLE(0640);

        private final int mode;

        AccessPolicy(int mode) {
            this.mode = mode;
        }
    }

    public static class AccessPolicyConverter implements ToNativeConverter {
        @Override
        public Object toNative(Object value, ToNativeContext context) {
            return ((AccessPolicy) value).mode;
        }

        @Override
        public Class<?> nativeType() {
            return Integer.class;
        }
    }
}
