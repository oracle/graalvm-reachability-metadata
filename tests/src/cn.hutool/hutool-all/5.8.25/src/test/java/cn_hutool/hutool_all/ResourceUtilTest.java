/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.collection.EnumerationIter;
import cn.hutool.core.io.resource.ResourceUtil;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceUtilTest {
    private static final String CLASS_RELATIVE_RESOURCE = "resource-util-relative.txt";
    private static final String CLASS_LOADER_RESOURCE = "hutool_resource_util/classloader-resource.txt";

    @Test
    void resolvesResourceRelativeToBaseClass() throws Exception {
        URL resource = ResourceUtil.getResource(CLASS_RELATIVE_RESOURCE, ResourceUtilTest.class);

        assertThat(resource).isNotNull();
        assertThat(read(resource)).isEqualTo("loaded-through-resource-util-class-get-resource\n");
    }

    @Test
    void resolvesResourceWithCurrentClassLoader() throws Exception {
        URL resource = ResourceUtil.getResource(CLASS_LOADER_RESOURCE);

        assertThat(resource).isNotNull();
        assertThat(read(resource)).isEqualTo("loaded-through-resource-util-classloader-get-resource\n");
    }

    @Test
    void iteratesResourcesWithExplicitClassLoader() throws Exception {
        EnumerationIter<URL> resources = ResourceUtil.getResourceIter(
                CLASS_LOADER_RESOURCE,
                ResourceUtilTest.class.getClassLoader());
        List<URL> urls = new ArrayList<>();
        while (resources.hasNext()) {
            urls.add(resources.next());
        }

        assertThat(urls).hasSize(1);
        assertThat(read(urls.get(0))).isEqualTo("loaded-through-resource-util-classloader-get-resource\n");
    }

    private static String read(URL resource) throws Exception {
        try (InputStream stream = resource.openStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
