/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Base64;
import java.util.EnumMap;

import com.google.gwt.user.server.rpc.EnumMapUtil;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class EnumMapUtilInnerMyObjectInputStreamTest {
    private static final String ORIGINAL_INTERNAL_NAME = "org_gwtproject/gwt_user/EnumMapUtilTest$SampleKey";
    private static final String DYNAMIC_BINARY_NAME = "org_gwtproject.gwt_user.DynamicEnumForFallback___";
    private static final String DYNAMIC_INTERNAL_NAME = DYNAMIC_BINARY_NAME.replace('.', '/');
    private static final byte[] ORIGINAL_ENUM_BYTES = Base64.getDecoder().decode(
            "yv66vgAAAEEAOAcAAgEAMW9yZ19nd3Rwcm9qZWN0L2d3dF91c2VyL0VudW1NYXBVdGlsVGVzdCRTYW1wbGVLZXkJAAEABAwABQAG" +
            "AQAFRklSU1QBADNMb3JnX2d3dHByb2plY3QvZ3d0X3VzZXIvRW51bU1hcFV0aWxUZXN0JFNhbXBsZUtleTsJAAEACAwACQAGAQAG" +
            "U0VDT05ECQABAAsMAAwADQEAByRWQUxVRVMBADRbTG9yZ19nd3Rwcm9qZWN0L2d3dF91c2VyL0VudW1NYXBVdGlsVGVzdCRTYW1w" +
            "bGVLZXk7CgAPABAHAA0MABEAEgEABWNsb25lAQAUKClMamF2YS9sYW5nL09iamVjdDsKABQAFQcAFgwAFwAYAQAOamF2YS9sYW5n" +
            "L0VudW0BAAd2YWx1ZU9mAQA1KExqYXZhL2xhbmcvQ2xhc3M7TGphdmEvbGFuZy9TdHJpbmc7KUxqYXZhL2xhbmcvRW51bTsKABQA" +
            "GgwAGwAcAQAGPGluaXQ+AQAWKExqYXZhL2xhbmcvU3RyaW5nO0kpVggABQoAAQAaCAAJCgABACEMACIAIwEAByR2YWx1ZXMBADYo" +
            "KVtMb3JnX2d3dHByb2plY3QvZ3d0X3VzZXIvRW51bU1hcFV0aWxUZXN0JFNhbXBsZUtleTsBAAZ2YWx1ZXMBAARDb2RlAQAPTGlu" +
            "ZU51bWJlclRhYmxlAQBHKExqYXZhL2xhbmcvU3RyaW5nOylMb3JnX2d3dHByb2plY3QvZ3d0X3VzZXIvRW51bU1hcFV0aWxUZXN0" +
            "JFNhbXBsZUtleTsBABJMb2NhbFZhcmlhYmxlVGFibGUBAARuYW1lAQASTGphdmEvbGFuZy9TdHJpbmc7AQAQTWV0aG9kUGFyYW1l" +
            "dGVycwEABHRoaXMBAAlTaWduYXR1cmUBAAMoKVYBAAg8Y2xpbml0PgEARUxqYXZhL2xhbmcvRW51bTxMb3JnX2d3dHByb2plY3Qv" +
            "Z3d0X3VzZXIvRW51bU1hcFV0aWxUZXN0JFNhbXBsZUtleTs+OwEAClNvdXJjZUZpbGUBABRFbnVtTWFwVXRpbFRlc3QuamF2YQEA" +
            "CE5lc3RIb3N0BwA1AQAnb3JnX2d3dHByb2plY3QvZ3d0X3VzZXIvRW51bU1hcFV0aWxUZXN0AQAMSW5uZXJDbGFzc2VzAQAJU2Ft" +
            "cGxlS2V5QDAAAQAUAAAAA0AZAAUABgAAQBkACQAGAAAQGgAMAA0AAAAFAAkAJAAjAAEAJQAAACIAAQAAAAAACrIACrYADsAAD7AA" +
            "AAABACYAAAAGAAEAAAAbAAkAFwAnAAIAJQAAADQAAgABAAAAChIBKrgAE8AAAbAAAAACACYAAAAGAAEAAAAbACgAAAAMAAEAAAAK" +
            "ACkAKgAAACsAAAAFAQAAgAAAAgAbABwAAwAlAAAAMQADAAMAAAAHKisctwAZsQAAAAIAJgAAAAYAAQAAABsAKAAAAAwAAQAAAAcA" +
            "LAAGAAAAKwAAAAkCAAAQAAAAEAAALQAAAAIALhAKACIAIwABACUAAAApAAQAAAAAABEFvQABWQOyAANTWQSyAAdTsAAAAAEAJgAA" +
            "AAYAAQAAABsACAAvAC4AAQAlAAAAQQAEAAAAAAAhuwABWRIdA7cAHrMAA7sAAVkSHwS3AB6zAAe4ACCzAAqxAAAAAQAmAAAADgAD" +
            "AAAAHAANAB0AGgAbAAQALQAAAAIAMAAxAAAAAgAyADMAAAACADQANgAAAAoAAQABADQAN0Aa"
    );

    @Test
    void resolveClassFallsBackToContextClassLoaderForEnumMapKeyType() throws Exception {
        try {
            Class<?> enumType = new DynamicEnumClassLoader(renamedEnumBytes()).loadDynamicEnum();
            ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(enumType.getClassLoader());
                Class<?> keyType = getKeyType(enumType.asSubclass(Enum.class));

                assertThat(keyType).isSameAs(enumType);
            } finally {
                Thread.currentThread().setContextClassLoader(previousContextClassLoader);
            }
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Class<?> getKeyType(Class<? extends Enum> enumType) throws Exception {
        EnumMap enumMap = new EnumMap(enumType);
        return EnumMapUtil.getKeyType(enumMap);
    }

    private static byte[] renamedEnumBytes() {
        assertThat(DYNAMIC_INTERNAL_NAME).hasSameSizeAs(ORIGINAL_INTERNAL_NAME);
        byte[] bytes = ORIGINAL_ENUM_BYTES.clone();
        byte[] originalName = ORIGINAL_INTERNAL_NAME.getBytes(UTF_8);
        byte[] dynamicName = DYNAMIC_INTERNAL_NAME.getBytes(UTF_8);
        int replacements = 0;
        for (int offset = 0; offset <= bytes.length - originalName.length; offset++) {
            if (matches(bytes, originalName, offset)) {
                System.arraycopy(dynamicName, 0, bytes, offset, dynamicName.length);
                replacements++;
            }
        }
        assertThat(replacements).isGreaterThan(0);
        return bytes;
    }

    private static boolean matches(byte[] bytes, byte[] candidate, int offset) {
        for (int index = 0; index < candidate.length; index++) {
            if (bytes[offset + index] != candidate[index]) {
                return false;
            }
        }
        return true;
    }

    private static final class DynamicEnumClassLoader extends ClassLoader {
        private final byte[] enumBytes;

        private DynamicEnumClassLoader(byte[] enumBytes) {
            super(null);
            this.enumBytes = enumBytes;
        }

        private Class<?> loadDynamicEnum() {
            return defineClass(DYNAMIC_BINARY_NAME, enumBytes, 0, enumBytes.length);
        }
    }
}
