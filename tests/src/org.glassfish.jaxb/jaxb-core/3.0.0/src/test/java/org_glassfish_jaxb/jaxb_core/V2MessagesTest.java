/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_core;

import org.glassfish.jaxb.core.v2.Messages;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class V2MessagesTest {
    @Test
    void formatsCoreV2MessagesFromResourceBundle() {
        String loadingError = Messages.ERROR_LOADING_CLASS.format("com.example.Book", "example/jaxb.index");
        String unsupportedProperty = Messages.UNSUPPORTED_PROPERTY.format("custom.property");
        String invalidMapType = Messages.INVALID_TYPE_IN_MAP.toString();

        assertThat(loadingError)
                .contains("com.example.Book", "example/jaxb.index")
                .doesNotContain("{0}", "{1}");
        assertThat(unsupportedProperty)
                .contains("custom.property")
                .doesNotContain("{0}");
        assertThat(invalidMapType)
                .isNotBlank()
                .isNotEqualTo(Messages.INVALID_TYPE_IN_MAP.name());
    }
}
