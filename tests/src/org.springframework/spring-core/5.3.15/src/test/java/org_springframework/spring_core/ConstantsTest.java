/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.core.Constants;
import org.springframework.util.ResourceUtils;

public class ConstantsTest {

    @Test
    void exposesPublicStaticFinalFieldsFromTargetClass() {
        Constants constants = new Constants(ResourceUtils.class);

        assertThat(constants.getClassName()).isEqualTo(ResourceUtils.class.getName());
        assertThat(constants.getSize()).isPositive();
        assertThat(constants.asString("classpath_url_prefix"))
                .isEqualTo(ResourceUtils.CLASSPATH_URL_PREFIX);
        assertThat(constants.asObject("FILE_URL_PREFIX"))
                .isEqualTo(ResourceUtils.FILE_URL_PREFIX);
        assertThat(constants.getNames("url_protocol"))
                .contains("URL_PROTOCOL_FILE", "URL_PROTOCOL_JAR", "URL_PROTOCOL_WAR");
        assertThat(constants.getValues("URL_PROTOCOL"))
                .contains(ResourceUtils.URL_PROTOCOL_FILE, ResourceUtils.URL_PROTOCOL_JAR);
        assertThat(constants.toCode(ResourceUtils.URL_PROTOCOL_FILE, "URL_PROTOCOL"))
                .isEqualTo("URL_PROTOCOL_FILE");
        assertThat(constants.toCodeForSuffix(ResourceUtils.JAR_URL_SEPARATOR, "separator"))
                .isEqualTo("JAR_URL_SEPARATOR");
    }

    @Test
    void derivesConstantGroupsFromPropertyNames() {
        Constants constants = new Constants(ResourceUtils.class);

        assertThat(constants.propertyToConstantNamePrefix("urlProtocol"))
                .isEqualTo("URL_PROTOCOL");
        assertThat(constants.getNamesForProperty("urlProtocol"))
                .contains("URL_PROTOCOL_FILE", "URL_PROTOCOL_JAR");
        assertThat(constants.getValuesForProperty("urlProtocol"))
                .contains(ResourceUtils.URL_PROTOCOL_FILE, ResourceUtils.URL_PROTOCOL_JAR);
        assertThat(constants.toCodeForProperty(ResourceUtils.URL_PROTOCOL_FILE, "urlProtocol"))
                .isEqualTo("URL_PROTOCOL_FILE");
    }

    @Test
    void reportsMissingOrIncompatibleConstants() {
        Constants constants = new Constants(ResourceUtils.class);

        assertThatThrownBy(() -> constants.asNumber("FILE_URL_PREFIX"))
                .isInstanceOf(Constants.ConstantException.class)
                .hasMessageContaining("not a Number");
        assertThatThrownBy(() -> constants.asObject("missing_constant"))
                .isInstanceOf(Constants.ConstantException.class)
                .hasMessageContaining("MISSING_CONSTANT")
                .hasMessageContaining("not found");
        assertThatThrownBy(() -> constants.toCode("missing", "URL_PROTOCOL"))
                .isInstanceOf(Constants.ConstantException.class)
                .hasMessageContaining("URL_PROTOCOL");
    }
}
