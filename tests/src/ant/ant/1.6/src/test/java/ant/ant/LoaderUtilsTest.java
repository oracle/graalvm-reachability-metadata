/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.apache.tools.ant.util.LoaderUtils;
import org.junit.jupiter.api.Test;

public class LoaderUtilsTest {
    @Test
    void usesOwnClassLoaderWhenResourceLookupHasNoClassLoader() {
        File source = LoaderUtils.getResourceSource(null, "ant/ant/does-not-exist.txt");

        assertThat(source).isNull();
    }
}
