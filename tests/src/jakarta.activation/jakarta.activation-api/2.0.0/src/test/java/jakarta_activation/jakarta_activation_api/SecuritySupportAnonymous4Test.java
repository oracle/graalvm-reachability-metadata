/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_activation.jakarta_activation_api;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecuritySupportAnonymous4Test {
    private static final String SECURITY_SUPPORT_CLASS_NAME = "javax.activation.SecuritySupport";
    private static final String ABSENT_RESOURCE_NAME =
        "jakarta_activation/jakarta_activation_api/absent-security-support-anonymous-4.resource";

    @Test
    void getSystemResourcesChecksSystemClassLoaderThroughPrivilegedAction() throws Throwable {
        MethodHandle getSystemResources = securitySupportLookup().findStatic(securitySupportType(), "getSystemResources",
            MethodType.methodType(URL[].class, String.class));

        URL[] urls = (URL[]) getSystemResources.invoke(ABSENT_RESOURCE_NAME);

        assertThat(urls).isNull();
    }

    private static MethodHandles.Lookup securitySupportLookup() throws ClassNotFoundException, IllegalAccessException {
        return MethodHandles.privateLookupIn(securitySupportType(), MethodHandles.lookup());
    }

    private static Class<?> securitySupportType() throws ClassNotFoundException {
        return Class.forName(SECURITY_SUPPORT_CLASS_NAME);
    }
}
