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
import org.openqa.selenium.support.locators.RelativeLocator.RelativeBy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class RelativeLocatorTest {
    @Test
    void relativeLocatorSerializesRootAndLocatorFilters() {
        RelativeBy locator = RelativeLocator.with(By.tagName("input"))
                .above(By.xpath("//*[@id='label']"))
                .near(By.cssSelector(".hint"), 25)
                .toRightOf(By.linkText("prefix"));

        By.Remotable.Parameters parameters = locator.getRemoteParameters();

        assertThat(parameters.using()).isEqualTo("relative");
        @SuppressWarnings("unchecked")
        Map<String, Object> value = (Map<String, Object>) parameters.value();
        assertThat(value).containsKey("root");
        assertThat(value).containsKey("filters");

        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) value.get("root");
        assertThat(root).containsEntry("tag name", "input");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> filters = (List<Map<String, Object>>) value.get("filters");
        assertThat(filters).hasSize(3);
        assertThat(filters.get(0)).containsEntry("kind", "above");
        assertThat(filters.get(0).get("args"))
                .isEqualTo(List.of(Map.of("xpath", "//*[@id='label']")));
        assertThat(filters.get(1)).containsEntry("kind", "near");
        assertThat(filters.get(1).get("args"))
                .isEqualTo(List.of(Map.of("css selector", ".hint"), 25));
        assertThat(filters.get(2)).containsEntry("kind", "right");
        assertThat(filters.get(2).get("args"))
                .isEqualTo(List.of(Map.of("link text", "prefix")));
    }

    @Test
    void rejectsLocatorsThatCannotBeSerializedToJson() {
        By nonSerializableLocator = new NonSerializableBy();

        assertThatThrownBy(() -> RelativeLocator.with(nonSerializableLocator))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(
                        "Locator must be serializable to JSON using a `toJson` method");
    }

    private static final class NonSerializableBy extends By {
        @Override
        public List<WebElement> findElements(SearchContext context) {
            return List.of();
        }
    }
}
