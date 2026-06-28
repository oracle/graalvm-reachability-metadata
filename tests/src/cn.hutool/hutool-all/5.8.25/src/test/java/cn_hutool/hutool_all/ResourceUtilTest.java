/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.io.resource.ResourceUtil;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceUtilTest {
    private static final String RESOURCE_NAME = "class-path-resource.txt";
    private static final String RESOURCE_PATH = "cn_hutool/hutool_all/" + RESOURCE_NAME;

    @Test
    void resolvesResourceRelativeToProvidedBaseClass() {
        URL resource = ResourceUtil.getResource(RESOURCE_NAME, ResourceUtilTest.class);

        assertThat(resource).isNotNull();
        assertThat(resource.toString()).endsWith(RESOURCE_PATH);
    }

    @Test
    void resolvesResourceFromContextClassLoader() {
        URL resource = ResourceUtil.getResource(RESOURCE_PATH);

        assertThat(resource).isNotNull();
        assertThat(resource.toString()).endsWith(RESOURCE_PATH);
    }

    @Test
    void listsResourcesFromContextClassLoader() {
        List<URL> resources = ResourceUtil.getResources(RESOURCE_PATH);

        assertThat(resources)
                .extracting(URL::toString)
                .anyMatch(url -> url.endsWith(RESOURCE_PATH));
    }
}
