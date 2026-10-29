/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.apache.tools.ant.util.LoaderUtils;
import org.junit.jupiter.api.Test;

public class LoaderUtilsTest {
    @Test
    void managesContextLoaderAndHandlesAbsentResources() {
        ClassLoader original = LoaderUtils.getContextClassLoader();
        ClassLoader loader = getClass().getClassLoader();
        try {
            LoaderUtils.setContextClassLoader(loader);
            assertSame(loader, LoaderUtils.getContextClassLoader());
            assertNull(LoaderUtils.getResourceSource(null, "ant-no-such-resource.properties"));
        } finally {
            LoaderUtils.setContextClassLoader(original);
        }
    }
}
