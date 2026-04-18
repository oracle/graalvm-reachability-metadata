/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.javax_activation_api;

import javax.activation.MimetypesFileTypeMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SecuritySupport4Test {
    @Test
    void nullContextClassLoaderStillLoadsMimeTypesFromMetaInf() {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalContextClassLoader = currentThread.getContextClassLoader();
        try {
            currentThread.setContextClassLoader(null);

            MimetypesFileTypeMap fileTypeMap = new MimetypesFileTypeMap();

            assertEquals(
                "application/x-security-support-4",
                fileTypeMap.getContentType("sample.securitysupport4")
            );
        } finally {
            currentThread.setContextClassLoader(originalContextClassLoader);
        }
    }
}
