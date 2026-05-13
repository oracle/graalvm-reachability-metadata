/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package nekohtml.nekohtml;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.junit.jupiter.api.Test;

public class SecuritySupportTest {
    private static final String SECURITY_SUPPORT_CLASS_NAME = "org.cyberneko.html.SecuritySupport";
    private static final String MISSING_RESOURCE_NAME = "nekohtml/nekohtml/SecuritySupportTest/missing-resource.txt";

    @Test
    void baseSecuritySupportQueriesSystemResourceWhenClassLoaderIsNull() throws Throwable {
        MethodHandle getResourceAsStream = getResourceAsStreamMethod();
        Object securitySupport = newSecuritySupport();

        try (InputStream stream = (InputStream) getResourceAsStream.invoke(securitySupport, null,
                        MISSING_RESOURCE_NAME)) {
            assertNull(stream);
        }
    }

    @Test
    void baseSecuritySupportQueriesResourceWithSuppliedClassLoader() throws Throwable {
        MethodHandle getResourceAsStream = getResourceAsStreamMethod();
        Object securitySupport = newSecuritySupport();
        ClassLoader classLoader = SecuritySupportTest.class.getClassLoader();

        try (InputStream stream = (InputStream) getResourceAsStream.invoke(securitySupport, classLoader,
                        MISSING_RESOURCE_NAME)) {
            assertNull(stream);
        }
    }

    private static Object newSecuritySupport() throws Throwable {
        Class<?> securitySupportClass = Class.forName(SECURITY_SUPPORT_CLASS_NAME);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(securitySupportClass, MethodHandles.lookup());
        MethodHandle constructor = lookup.findConstructor(securitySupportClass, MethodType.methodType(void.class));
        return constructor.invoke();
    }

    private static MethodHandle getResourceAsStreamMethod() throws ReflectiveOperationException {
        Class<?> securitySupportClass = Class.forName(SECURITY_SUPPORT_CLASS_NAME);
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(securitySupportClass, MethodHandles.lookup());
        MethodType methodType = MethodType.methodType(InputStream.class, ClassLoader.class, String.class);
        return lookup.findVirtual(securitySupportClass, "getResourceAsStream", methodType);
    }
}
