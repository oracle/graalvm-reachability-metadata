/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_blackbird;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.junit.jupiter.api.Test;

public class ReflectionHackInnerJava8Test {
    @Test
    void roundTripsPackageVisibleBeanThroughTheRegisteredModule() throws Exception {
        PackageVisibleBean value = CrossLoaderAccessTest.MAPPER.readValue(
                """
                { "code": "private-lookup" }
                """,
                PackageVisibleBean.class);

        assertThat(value.readCode()).isEqualTo("private-lookup");
        assertThat(CrossLoaderAccessTest.MAPPER.writeValueAsString(value))
                .isEqualTo("{\"code\":\"private-lookup\"}");
    }

    static final class PackageVisibleBean {
        private String code;

        PackageVisibleBean() {
        }

        @JsonProperty("code")
        String readCode() {
            return code;
        }

        @JsonProperty("code")
        void writeCode(String code) {
            this.code = code;
        }
    }
}
