/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_seleniumhq_selenium.selenium_support;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.Point;
import org.openqa.selenium.Rectangle;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.WrapsElement;
import org.openqa.selenium.interactions.Locatable;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.pagefactory.DefaultFieldDecorator;
import org.openqa.selenium.support.pagefactory.ElementLocator;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultFieldDecoratorTest {
    @Test
    void decoratesWebElementAndAnnotatedWebElementListWithLazyProxies() throws NoSuchFieldException {
        RecordingWebElement element = new RecordingWebElement("button", "Submit");
        RecordingWebElement firstItem = new RecordingWebElement("li", "First");
        RecordingWebElement secondItem = new RecordingWebElement("li", "Second");
        RecordingElementLocator locator = new RecordingElementLocator(
                element,
                Arrays.asList(firstItem, secondItem));
        DefaultFieldDecorator decorator = new DefaultFieldDecorator(field -> locator);

        WebElement elementProxy = (WebElement) decorator.decorate(
                TestPage.class.getClassLoader(),
                fieldNamed("submitButton"));
        @SuppressWarnings("unchecked")
        List<WebElement> listProxy = (List<WebElement>) decorator.decorate(
                TestPage.class.getClassLoader(),
                fieldNamed("resultItems"));

        assertThat(elementProxy).isInstanceOf(WrapsElement.class);
        assertThat(elementProxy).isInstanceOf(Locatable.class);
        assertThat(elementProxy.getText()).isEqualTo("Submit");
        assertThat(((WrapsElement) elementProxy).getWrappedElement()).isSameAs(element);
        assertThat(listProxy).hasSize(2);
        assertThat(listProxy.get(0)).isSameAs(firstItem);
        assertThat(listProxy.get(1).getText()).isEqualTo("Second");
        assertThat(locator.calls()).contains("findElement", "findElements");
        assertThat(element.calls()).containsExactly("getText");
        assertThat(secondItem.calls()).containsExactly("getText");
    }

    private static Field fieldNamed(String name) throws NoSuchFieldException {
        return TestPage.class.getDeclaredField(name);
    }

    private static final class TestPage {
        private WebElement submitButton;

        @FindBy(css = ".result")
        private List<WebElement> resultItems;
    }

    private static final class RecordingElementLocator implements ElementLocator {
        private final List<String> calls;
        private final WebElement element;
        private final List<WebElement> elements;

        private RecordingElementLocator(WebElement element, List<WebElement> elements) {
            this.calls = new ArrayList<>();
            this.element = element;
            this.elements = elements;
        }

        @Override
        public WebElement findElement() {
            calls.add("findElement");
            return element;
        }

        @Override
        public List<WebElement> findElements() {
            calls.add("findElements");
            return elements;
        }

        private List<String> calls() {
            return calls;
        }
    }

    private static final class RecordingWebElement implements WebElement {
        private final List<String> calls = new ArrayList<>();
        private final String tagName;
        private final String text;

        private RecordingWebElement(String tagName, String text) {
            this.tagName = tagName;
            this.text = text;
        }

        @Override
        public void click() {
            calls.add("click");
        }

        @Override
        public void submit() {
            calls.add("submit");
        }

        @Override
        public void sendKeys(CharSequence... keysToSend) {
            calls.add("sendKeys");
        }

        @Override
        public void clear() {
            calls.add("clear");
        }

        @Override
        public String getTagName() {
            calls.add("getTagName");
            return tagName;
        }

        @Override
        public String getAttribute(String name) {
            calls.add("getAttribute:" + name);
            return null;
        }

        @Override
        public boolean isSelected() {
            calls.add("isSelected");
            return false;
        }

        @Override
        public boolean isEnabled() {
            calls.add("isEnabled");
            return true;
        }

        @Override
        public String getText() {
            calls.add("getText");
            return text;
        }

        @Override
        public List<WebElement> findElements(By by) {
            calls.add("findElements");
            return Collections.emptyList();
        }

        @Override
        public WebElement findElement(By by) {
            calls.add("findElement");
            throw new UnsupportedOperationException("Nested elements are not needed by this test");
        }

        @Override
        public boolean isDisplayed() {
            calls.add("isDisplayed");
            return true;
        }

        @Override
        public Point getLocation() {
            calls.add("getLocation");
            return new Point(0, 0);
        }

        @Override
        public Dimension getSize() {
            calls.add("getSize");
            return new Dimension(10, 10);
        }

        @Override
        public Rectangle getRect() {
            calls.add("getRect");
            return new Rectangle(getLocation(), getSize());
        }

        @Override
        public String getCssValue(String propertyName) {
            calls.add("getCssValue:" + propertyName);
            return "";
        }

        @Override
        public <X> X getScreenshotAs(OutputType<X> target) throws WebDriverException {
            calls.add("getScreenshotAs");
            return null;
        }

        private List<String> calls() {
            return calls;
        }
    }
}
