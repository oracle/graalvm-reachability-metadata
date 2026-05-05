/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.io.resource.ClassPathResource;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassPathResourceTest {
    private static final String CLASS_RELATIVE_RESOURCE = "classpath-resource-relative.txt";
    private static final String CLASS_LOADER_RESOURCE = "hutool_classpath/classloader-resource.txt";
    private static final String DEFAULT_RESOURCE = "hutool_classpath/system-resource.txt";

    @Test
    void loadsResourceRelativeToClass() throws Exception {
        ClassPathResource resource = new ClassPathResource(CLASS_RELATIVE_RESOURCE, ClassPathResourceTest.class);

        assertThat(resource.getPath()).isEqualTo(CLASS_RELATIVE_RESOURCE);
        assertThat(resource.getName()).isEqualTo(CLASS_RELATIVE_RESOURCE);
        assertThat(read(resource)).isEqualTo("loaded-through-class-get-resource\n");
    }

    @Test
    void loadsResourceThroughExplicitClassLoader() throws Exception {
        ClassLoader classLoader = ClassPathResourceTest.class.getClassLoader();

        ClassPathResource resource = new ClassPathResource(CLASS_LOADER_RESOURCE, classLoader);

        assertThat(resource.getPath()).isEqualTo(CLASS_LOADER_RESOURCE);
        assertThat(resource.getClassLoader()).isSameAs(classLoader);
        assertThat(read(resource)).isEqualTo("loaded-through-classloader-get-resource\n");
    }

    @Test
    void loadsResourceThroughDefaultClassPathLookup() throws Exception {
        ClassPathResource resource = new ClassPathResource(DEFAULT_RESOURCE);

        assertThat(resource.getPath()).isEqualTo(DEFAULT_RESOURCE);
        assertThat(resource.toString()).isEqualTo("classpath:" + DEFAULT_RESOURCE);
        assertThat(read(resource)).isEqualTo("loaded-through-default-resource-resolution\n");
    }

    private static String read(ClassPathResource resource) throws Exception {
        try (InputStream stream = resource.getStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
