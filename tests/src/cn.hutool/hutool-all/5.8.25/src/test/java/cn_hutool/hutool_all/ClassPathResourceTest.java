/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.io.resource.ClassPathResource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassPathResourceTest {
    private static final String RESOURCE_NAME = "class-path-resource.txt";
    private static final String RESOURCE_PATH = "cn_hutool/hutool_all/" + RESOURCE_NAME;
    private static final String RESOURCE_CONTENT = "class path resource loaded by Hutool";

    @Test
    void resolvesResourceRelativeToProvidedClass() {
        ClassPathResource resource = new ClassPathResource(
                RESOURCE_NAME, ClassPathResourceTest.class);

        assertThat(resource.getName()).isEqualTo(RESOURCE_NAME);
        assertThat(resource.getPath()).isEqualTo(RESOURCE_NAME);
        assertThat(resource.getUrl()).isNotNull();
        assertThat(resource.readUtf8Str()).isEqualTo(RESOURCE_CONTENT);
        assertThat(resource.toString()).isEqualTo("classpath:" + RESOURCE_NAME);
    }

    @Test
    void resolvesResourceWithProvidedClassLoader() {
        ClassLoader classLoader = ClassPathResourceTest.class.getClassLoader();
        ClassPathResource resource = new ClassPathResource(RESOURCE_PATH, classLoader);

        assertThat(resource.getClassLoader()).isSameAs(classLoader);
        assertThat(resource.getName()).isEqualTo(RESOURCE_NAME);
        assertThat(resource.getPath()).isEqualTo(RESOURCE_PATH);
        assertThat(resource.getUrl()).isNotNull();
        assertThat(resource.readUtf8Str()).isEqualTo(RESOURCE_CONTENT);
    }
}
