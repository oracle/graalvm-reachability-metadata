/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_support;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.locators.RelativeLocator;

import static org.assertj.core.api.Assertions.assertThat;

public class RelativeLocatorTest {
    @Test
    void withBuiltInLocatorBuildsSerializableRemoteParameters() {
        RelativeLocator.RelativeBy locator = RelativeLocator.with(By.id("submit"));

        By.Remotable.Parameters parameters = locator.getRemoteParameters();

        assertThat(parameters.using()).isEqualTo("relative");
        assertThat(parameters.value()).isInstanceOf(Map.class);
        Map<?, ?> value = (Map<?, ?>) parameters.value();
        assertThat(value.containsKey("root")).isTrue();
        assertThat(value.get("filters")).isEqualTo(List.of());
    }
}
