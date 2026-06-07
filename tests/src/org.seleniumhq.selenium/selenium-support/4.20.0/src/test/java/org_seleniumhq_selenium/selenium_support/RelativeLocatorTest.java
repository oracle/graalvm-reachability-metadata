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
import org.openqa.selenium.SearchContext;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.locators.RelativeLocator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.MAP;

public class RelativeLocatorTest {
    @Test
    void withAcceptsLocatorsThatCanBeSerializedToJson() {
        By relativeLocator = RelativeLocator.with(new JsonSerializableBy("css selector", ".result"));
        By.Remotable.Parameters parameters = ((By.Remotable) relativeLocator).getRemoteParameters();

        assertThat(parameters.using()).isEqualTo("relative");
        assertThat(parameters.value())
                .isInstanceOf(Map.class)
                .asInstanceOf(MAP)
                .containsEntry("root", Map.of("css selector", ".result"))
                .containsEntry("filters", List.of());
    }

    public static final class JsonSerializableBy extends By {
        private final String using;
        private final String value;

        public JsonSerializableBy(String using, String value) {
            this.using = using;
            this.value = value;
        }

        @Override
        public List<WebElement> findElements(SearchContext context) {
            return List.of();
        }

        public Map<String, Object> toJson() {
            return Map.of("using", using, "value", value);
        }
    }
}
