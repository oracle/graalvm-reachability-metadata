/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_thymeleaf.thymeleaf;

import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.thymeleaf.standard.serializer.StandardJavaScriptSerializer;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardJavaScriptSerializerInnerDefaultStandardJavaScriptSerializerTest {

    @Test
    void serializeValueUsesBeanGetterIntrospectionForObjects() {
        StandardJavaScriptSerializer serializer = new StandardJavaScriptSerializer(false);
        JavaScriptBean bean = new JavaScriptBean("hello");
        StringWriter writer = new StringWriter();

        serializer.serializeValue(bean, writer);

        assertThat(writer.toString()).isEqualTo("{\"message\":\"hello\"}");
        assertThat(bean.messageReadCount()).isEqualTo(1);
    }

    public static final class JavaScriptBean {

        private final AtomicInteger messageReadCount = new AtomicInteger();
        private final String message;

        private JavaScriptBean(String message) {
            this.message = message;
        }

        public String getMessage() {
            this.messageReadCount.incrementAndGet();
            return this.message;
        }

        int messageReadCount() {
            return this.messageReadCount.get();
        }
    }
}
