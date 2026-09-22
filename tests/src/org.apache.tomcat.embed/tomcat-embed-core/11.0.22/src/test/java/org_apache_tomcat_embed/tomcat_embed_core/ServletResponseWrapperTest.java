/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import jakarta.servlet.ServletResponseWrapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ServletResponseWrapperTest {

    @Test
    void rejectsNullWrappedResponse() {
        assertThatThrownBy(() -> new ServletResponseWrapper(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Response");
    }
}
