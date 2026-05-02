/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_jackson.jackson_mapper_asl;

import org.codehaus.jackson.map.ObjectMapper;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StdDeserializerInnerClassDeserializerTest {
    @Test
    void resolvesTopLevelClassFromJsonString() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String className = runtimeJdkClassName();

        try {
            Class<?> resolved = mapper.readValue(quoted(className), Class.class);

            assertThat(resolved.getName()).isEqualTo(className);
        } catch (Error error) {
            verifyUnsupportedFeatureError(error);
        }
    }

    private static void verifyUnsupportedFeatureError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    private static String quoted(String value) {
        return '"' + value + '"';
    }

    private static String runtimeJdkClassName() {
        return System.getProperty(StdDeserializerInnerClassDeserializerTest.class.getName(),
                String.join(".", "java", "util", "LinkedHashMap"));
    }
}
