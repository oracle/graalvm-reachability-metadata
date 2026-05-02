/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.i18n.client.Messages;
import com.google.gwt.junit.FakeMessagesMaker;
import com.google.gwt.safehtml.shared.SafeHtml;

import org.junit.jupiter.api.Test;

public class FakeMessagesMakerTest {
    @Test
    void createsMessagesProxyReturningMethodNamesAndArguments() {
        GreetingMessages messages = FakeMessagesMaker.create(GreetingMessages.class);

        assertThat(messages.greeting()).isEqualTo("greeting");
        assertThat(messages.itemCount("cart", 3)).isEqualTo("itemCount[cart, 3]");
    }

    @Test
    void returnsSafeHtmlWhenDeclaredReturnTypeRequiresIt() {
        GreetingMessages messages = FakeMessagesMaker.create(GreetingMessages.class);

        SafeHtml html = messages.safeGreeting("<Ada>");

        assertThat(html.asString()).isEqualTo("safeGreeting[&lt;Ada&gt;]");
    }

    public interface GreetingMessages extends Messages {
        String greeting();

        String itemCount(String containerName, int count);

        SafeHtml safeGreeting(String userName);
    }
}
