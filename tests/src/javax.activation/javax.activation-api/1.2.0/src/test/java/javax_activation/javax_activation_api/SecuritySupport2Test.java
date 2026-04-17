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

public class SecuritySupport2Test {
    @Test
    void defaultMimeTypesResourceIsLoadedFromClasspath() {
        MimetypesFileTypeMap fileTypeMap = new MimetypesFileTypeMap();

        assertEquals(
            "application/x-security-support-2",
            fileTypeMap.getContentType("sample.securitysupport2")
        );
        assertEquals("application/octet-stream", fileTypeMap.getContentType("sample.unknown"));
    }
}
