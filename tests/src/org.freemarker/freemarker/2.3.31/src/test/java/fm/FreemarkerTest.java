/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package fm;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

public class FreemarkerTest {

    @Test
    void test() throws IOException, TemplateException {
        Map<String, Object> root = new HashMap<>();
        root.put("user", "Big Joe");
        Product latest = new Product();
        latest.setUrl("products/greenmouse.html");
        latest.setName("green mouse");
        root.put("latestProduct", latest);

        Configuration configuration = new Configuration(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS);
        configuration.setClassForTemplateLoading(this.getClass(), "/");
        Template template = configuration.getTemplate("test.ftlh");
        StringWriter writer = new StringWriter();
        template.process(root, writer);

        Assertions.assertThat(writer.toString()).isNotEmpty();
    }

}
