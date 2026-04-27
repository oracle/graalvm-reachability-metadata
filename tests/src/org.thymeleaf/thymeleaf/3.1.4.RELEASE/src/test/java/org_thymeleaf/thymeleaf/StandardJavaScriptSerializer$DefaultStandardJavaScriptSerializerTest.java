/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.thymeleaf.standard.serializer.StandardJavaScriptSerializer;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardJavaScriptSerializer$DefaultStandardJavaScriptSerializerTest {

    @Test
    void serializeValueIntrospectsBeanPropertiesWhenJacksonIsDisabled() {
        StandardJavaScriptSerializer serializer = new StandardJavaScriptSerializer(false);
        StringWriter writer = new StringWriter();

        serializer.serializeValue(new JavaScriptBean("thymeleaf", true), writer);

        assertThat(writer.toString())
                .startsWith("{")
                .endsWith("}")
                .contains("\"library\":\"thymeleaf\"")
                .contains("\"stable\":true")
                .doesNotContain("\"class\"");
    }

    private static final class JavaScriptBean {

        private final String library;
        private final boolean stable;

        private JavaScriptBean(final String library, final boolean stable) {
            this.library = library;
            this.stable = stable;
        }

        public String getLibrary() {
            return this.library;
        }

        public boolean isStable() {
            return this.stable;
        }
    }
}
