/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.support.ApplicationObjectSupport;
import org.springframework.context.support.GenericXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ResourceBundleMessageSourceTest {
    @Test
    void resolvesLocalizedMessagesAndInitializesMessageAwareBean() {
        try (GenericXmlApplicationContext context = new GenericXmlApplicationContext(
                "classpath:org_springframework/spring_context/messages.xml")) {
            Greeting greeting = context.getBean(Greeting.class);
            assertEquals("Welcome, Ada!", greeting.welcome("Ada"));
            assertEquals("Bonjour, Ada!", context.getMessage("welcome", new Object[]{"Ada"}, Locale.FRENCH));
            assertEquals("parent message", context.getMessage("parent", null, Locale.ENGLISH));
            assertEquals("default message", context.getMessage("absent", null, "default message", Locale.ENGLISH));
        }
    }

    public static class Greeting extends ApplicationObjectSupport implements MessageSourceAware {
        private MessageSource messages;

        @Override
        public void setMessageSource(MessageSource messageSource) {
            this.messages = messageSource;
        }

        public String welcome(String name) {
            String fromAware = messages.getMessage("welcome", new Object[]{name}, Locale.ENGLISH);
            String fromContext = getMessageSourceAccessor().getMessage("welcome", new Object[]{name}, Locale.ENGLISH);
            assertEquals(fromContext, fromAware);
            return fromAware;
        }
    }
}
