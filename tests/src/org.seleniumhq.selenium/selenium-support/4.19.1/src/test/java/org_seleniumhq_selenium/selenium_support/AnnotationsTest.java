/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_support;

import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.pagefactory.Annotations;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationsTest {
    @Test
    void buildByInstantiatesFindByBuilderFromPageFactoryFinderAnnotation() throws NoSuchFieldException {
        Field field = SearchPage.class.getDeclaredField("submitButton");

        By locator = new Annotations(field).buildBy();

        assertThat(locator.toString()).isEqualTo("By.id: submit-button");
    }

    private static final class SearchPage {
        @FindBy(id = "submit-button")
        private WebElement submitButton;
    }
}
