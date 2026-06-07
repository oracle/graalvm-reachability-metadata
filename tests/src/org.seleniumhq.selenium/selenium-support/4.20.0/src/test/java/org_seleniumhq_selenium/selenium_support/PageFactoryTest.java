/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_support;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.pagefactory.FieldDecorator;

import static org.assertj.core.api.Assertions.assertThat;

public class PageFactoryTest {
    @Test
    void initElementsUsesWebDriverConstructorWhenAvailable() {
        WebDriverConstructorPage page = PageFactory.initElements((WebDriver) null, WebDriverConstructorPage.class);

        assertThat(page.wasConstructedWithWebDriverConstructor()).isTrue();
    }

    @Test
    void initElementsFallsBackToNoArgumentConstructor() {
        NoArgumentConstructorPage page = PageFactory.initElements((WebDriver) null, NoArgumentConstructorPage.class);

        assertThat(page.wasConstructed()).isTrue();
    }

    @Test
    void initElementsDecoratesPrivateFields() {
        DecoratedPage page = new DecoratedPage();
        FieldDecorator decorator = (loader, field) -> "decorated value";

        PageFactory.initElements(decorator, page);

        assertThat(page.getMessage()).isEqualTo("decorated value");
    }

    public static class WebDriverConstructorPage {
        private final boolean constructedWithWebDriverConstructor;

        public WebDriverConstructorPage(WebDriver driver) {
            this.constructedWithWebDriverConstructor = true;
        }

        boolean wasConstructedWithWebDriverConstructor() {
            return constructedWithWebDriverConstructor;
        }
    }

    public static class NoArgumentConstructorPage {
        private final boolean constructed;

        public NoArgumentConstructorPage() {
            this.constructed = true;
        }

        boolean wasConstructed() {
            return constructed;
        }
    }

    public static class DecoratedPage {
        private String message;

        String getMessage() {
            return message;
        }
    }
}
