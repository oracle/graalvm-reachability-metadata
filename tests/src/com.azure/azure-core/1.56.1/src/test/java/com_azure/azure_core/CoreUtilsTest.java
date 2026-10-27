/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azure.core.util.CoreUtils;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class CoreUtilsTest {
    @Test
    void loadsPropertiesAsAnImmutableMap() {
        Map<String, String> properties = CoreUtils.getProperties("azure-core-test.properties");

        assertThat(properties).containsEntry("service", "azure-core").containsEntry("mode", "local");
        assertThatThrownBy(() -> properties.put("other", "value")).isInstanceOf(UnsupportedOperationException.class);
    }
}
