/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.sdk_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.internal.util.Mimetype;

public class MimetypeTest {
    @Test
    void getInstanceInitializesClasspathBackedMimetypeRegistry() {
        Mimetype mimetype = Mimetype.getInstance();

        assertThat(mimetype).isSameAs(Mimetype.getInstance());
        assertThat(mimetype.getMimetype(Path.of("payload.unknown-extension")))
                .isEqualTo(Mimetype.MIMETYPE_OCTET_STREAM);
    }
}
