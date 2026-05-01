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

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

public class ResourceUtilTest {
    @Test
    public void resolvesAbsoluteResourcePathRelativeToBaseClass() {
        URL resource = ResourceUtil.getResource(
                "/cn_hutool/hutool_all/resource-util-missing-class-resource.txt",
                ResourceUtilTest.class);

        assertThat(resource).isNull();
    }

    @Test
    public void resolvesResourcePathWithDefaultClassLoader() {
        URL resource = ResourceUtil.getResource(
                "cn_hutool/hutool_all/resource-util-missing-class-loader-resource.txt");

        assertThat(resource).isNull();
    }

    @Test
    public void iteratesResourcesWithDefaultClassLoader() {
        EnumerationIter<URL> resources = ResourceUtil.getResourceIter(
                "cn_hutool/hutool_all/resource-util-missing-resource-directory");

        assertThat(resources.hasNext()).isFalse();
    }
}
