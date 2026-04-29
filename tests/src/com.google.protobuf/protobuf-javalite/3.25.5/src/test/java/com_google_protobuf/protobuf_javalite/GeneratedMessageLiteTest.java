/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_javalite;

import com.google.protobuf.GeneratedMessageLitePackageAccess;
import com.google.protobuf.StringValue;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GeneratedMessageLiteTest {
    @Test
    void reflectiveToStringInvokesGeneratedAccessors() {
        StringValue message = StringValue.of("lite text format coverage");

        String textFormat = message.toString();

        assertThat(textFormat).contains("value: \"lite text format coverage\"");
    }

    @Test
    void generatedCodeHelperResolvesAndInvokesPublicAccessor() {
        StringValue message = StringValue.of("generated helper coverage");

        Object value =
                GeneratedMessageLitePackageAccess.readValueWithGeneratedMessageLiteHelpers(message);

        assertThat(value).isEqualTo("generated helper coverage");
    }
}
