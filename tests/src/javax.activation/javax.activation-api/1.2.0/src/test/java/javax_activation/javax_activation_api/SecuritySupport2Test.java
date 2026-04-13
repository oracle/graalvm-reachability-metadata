/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_activation.javax_activation_api;

import javax.activation.MimetypesFileTypeMap;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers dynamic access in {@code javax.activation.SecuritySupport$2}.
 */
public final class SecuritySupport2Test {

    @Test
    void defaultMimeTypesResourceIsLoadedViaClassResourceLookup() {
        MimetypesFileTypeMap fileTypeMap = new MimetypesFileTypeMap();

        String contentType = fileTypeMap.getContentType("sample.securitysupport2");

        assertThat(contentType).isEqualTo("text/x-security-support-2");
    }
}
