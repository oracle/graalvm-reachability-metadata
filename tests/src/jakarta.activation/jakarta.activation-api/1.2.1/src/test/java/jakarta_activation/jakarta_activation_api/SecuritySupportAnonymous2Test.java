/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_activation.jakarta_activation_api;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecuritySupportAnonymous2Test {
    private static final String SECURITY_SUPPORT_CLASS_NAME = "javax.activation.SecuritySupport";

    @Test
    void getResourceAsStreamLoadsResourceThroughPrivilegedAction() throws Throwable {
        MethodHandle getResourceAsStream = securitySupportLookup().findStatic(securitySupportType(),
            "getResourceAsStream", MethodType.methodType(InputStream.class, Class.class, String.class));

        try (InputStream stream = (InputStream) getResourceAsStream.invoke(SecuritySupportAnonymous2Test.class,
            "security-support-resource.txt")) {
            assertThat(stream).isNotNull();
            assertThat(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("security-support\n");
        }
    }

    private static MethodHandles.Lookup securitySupportLookup() throws ClassNotFoundException, IllegalAccessException {
        return MethodHandles.privateLookupIn(securitySupportType(), MethodHandles.lookup());
    }

    private static Class<?> securitySupportType() throws ClassNotFoundException {
        return Class.forName(SECURITY_SUPPORT_CLASS_NAME);
    }
}
