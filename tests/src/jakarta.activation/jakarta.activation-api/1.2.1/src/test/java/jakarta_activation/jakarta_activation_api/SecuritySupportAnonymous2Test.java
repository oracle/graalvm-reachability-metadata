/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_activation.jakarta_activation_api;

import static org.assertj.core.api.Assertions.assertThat;

import javax.activation.MimetypesFileTypeMap;

import org.junit.jupiter.api.Test;

public class SecuritySupportAnonymous2Test {

    @Test
    void mimetypesFileTypeMapLoadsDefaultMimeTypesResourceThroughLibraryClass() {
        MimetypesFileTypeMap fileTypeMap = new MimetypesFileTypeMap();

        assertThat(fileTypeMap.getContentType("attachment.txt")).isEqualTo("text/plain");
        assertThat(fileTypeMap.getContentType("attachment.unknown-extension"))
                .isEqualTo("application/octet-stream");
    }
}
