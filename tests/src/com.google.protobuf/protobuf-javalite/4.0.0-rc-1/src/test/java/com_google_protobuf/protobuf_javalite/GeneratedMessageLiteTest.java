/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.StringValue;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

public class GeneratedMessageLiteTest {
    @Test
    void toStringPrintsLiteMessageFieldsThroughGeneratedAccessors() {
        StringValue message = StringValue.of("generated-message-lite");

        String textFormat = message.toString();

        assertThat(textFormat)
                .startsWith("# com.google.protobuf.StringValue@")
                .contains("value: \"generated-message-lite\"");
    }

    @Test
    void generatedAccessorLookupFindsPublicDefaultInstanceMethod() throws Exception {
        Method helper = GeneratedMessageLite.class.getDeclaredMethod(
                "getMethodOrDie", Class.class, String.class, Class[].class);
        helper.setAccessible(true);

        Method defaultInstanceMethod = (Method) helper.invoke(
                null, StringValue.class, "getDefaultInstance", new Class<?>[0]);

        assertThat(defaultInstanceMethod.getDeclaringClass()).isEqualTo(StringValue.class);
        assertThat(defaultInstanceMethod.getName()).isEqualTo("getDefaultInstance");
        assertThat(defaultInstanceMethod.invoke(null)).isSameAs(StringValue.getDefaultInstance());
    }
}
