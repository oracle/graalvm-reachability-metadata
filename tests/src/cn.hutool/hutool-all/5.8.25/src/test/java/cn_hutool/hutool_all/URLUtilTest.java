/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.util.URLUtil;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class URLUtilTest {
    private static final String CLASS_LOADER_RESOURCE = "hutool_classpath/classloader-resource.txt";

    @Test
    void resolvesClasspathUrlThroughCurrentClassLoader() throws Exception {
        URL resource = URLUtil.url(URLUtil.CLASSPATH_URL_PREFIX + CLASS_LOADER_RESOURCE);

        assertThat(resource).isNotNull();
        assertThat(read(resource)).isEqualTo("loaded-through-classloader-get-resource\n");
    }

    private static String read(URL resource) throws Exception {
        try (InputStream stream = resource.openStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
