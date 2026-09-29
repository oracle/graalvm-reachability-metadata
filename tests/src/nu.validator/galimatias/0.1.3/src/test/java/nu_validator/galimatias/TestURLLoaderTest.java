/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package nu_validator.galimatias;

import static org.assertj.core.api.Assertions.assertThat;

import io.mola.galimatias.TestURL;
import io.mola.galimatias.TestURLLoader;
import java.util.List;
import org.junit.jupiter.api.Test;

public class TestURLLoaderTest {
    @Test
    void loadsAndNormalizesThePackagedHostDataset() {
        List<TestURL> testURLs =
                TestURLLoader.loadTestURLs("/test/resources/data/urltestdata_host_whatwg.txt");

        assertThat(testURLs).isNotEmpty();
        TestURL firstTest = testURLs.get(0);
        assertThat(firstTest.rawURL).isEqualTo("http://ExAmPlE.CoM");
        assertThat(firstTest.rawBaseURL).isEqualTo("http://other.com/");
        assertThat(firstTest.parsedBaseURL.toString()).isEqualTo("http://other.com/");
        assertThat(firstTest.parsedURL.toString()).isEqualTo("http://example.com/");
        assertThat(firstTest.parsedURL.host().toString()).isEqualTo("example.com");
    }
}
