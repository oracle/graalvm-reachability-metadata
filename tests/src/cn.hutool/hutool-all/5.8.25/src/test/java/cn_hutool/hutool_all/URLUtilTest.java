/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.util.URLUtil;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class URLUtilTest {
    @Test
    public void resolvesClasspathUrlWithDefaultClassLoader() {
        URL resource = URLUtil.url("classpath:cn_hutool/hutool_all/url-util-missing-resource.txt");

        assertThat(resource).isNull();
    }
}
