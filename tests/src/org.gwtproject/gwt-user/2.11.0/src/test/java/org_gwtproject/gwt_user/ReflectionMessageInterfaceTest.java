/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.i18n.client.LocalizableResource.Description;
import com.google.gwt.i18n.client.LocalizableResource.Key;
import com.google.gwt.i18n.client.Messages;
import com.google.gwt.i18n.client.Messages.DefaultMessage;
import com.google.gwt.i18n.server.GwtLocaleFactoryImpl;
import com.google.gwt.i18n.server.Message;
import com.google.gwt.i18n.server.MessageProcessingException;
import com.google.gwt.i18n.server.MessageFormatUtils.MessageStyle;
import com.google.gwt.i18n.server.Type;
import com.google.gwt.i18n.server.impl.ReflectionMessageInterface;
import com.google.gwt.i18n.shared.GwtLocaleFactory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class ReflectionMessageInterfaceTest {
    private static final GwtLocaleFactory LOCALE_FACTORY = new GwtLocaleFactoryImpl();

    @Test
    void discoversPublicMessagesFromLocalizableInterface() throws MessageProcessingException {
        ReflectionMessageInterface messageInterface =
                new ReflectionMessageInterface(LOCALE_FACTORY, CatalogMessages.class);

        List<Message> messages = toList(messageInterface.getMessages());

        assertThat(messages)
                .extracting(Message::getMethodName)
                .containsExactly("createdNotification", "deletedNotification");
        assertThat(messages)
                .extracting(Message::getKey)
                .containsExactly("catalog.created", "catalog.deleted");
        assertThat(messages)
                .extracting(Message::getDefaultMessage)
                .containsExactly("Created {0}", "Deleted {0}");
    }

    @Test
    void exposesReflectedMessageMetadata() throws MessageProcessingException {
        ReflectionMessageInterface messageInterface =
                new ReflectionMessageInterface(LOCALE_FACTORY, CatalogMessages.class);

        Message message = toList(messageInterface.getMessages()).get(0);

        assertThat(messageInterface.getPackageName()).isEqualTo("org_gwtproject.gwt_user");
        assertThat(messageInterface.getClassName())
                .isEqualTo("ReflectionMessageInterfaceTest.CatalogMessages");
        assertThat(messageInterface.getQualifiedName())
                .isEqualTo("org_gwtproject.gwt_user.ReflectionMessageInterfaceTest.CatalogMessages");
        assertThat(message.getDescription()).isEqualTo("Notification shown after creating an item");
        assertThat(message.getMessageStyle()).isEqualTo(MessageStyle.MESSAGE_FORMAT);
        assertThat(message.getReturnType()).isEqualTo(Type.STRING);
        assertThat(message.getParameters())
                .singleElement()
                .satisfies(parameter -> {
                    assertThat(parameter.getName()).isEqualTo("arg0");
                    assertThat(parameter.getType()).isEqualTo(Type.STRING);
                });
    }

    private static List<Message> toList(Iterable<Message> messages) {
        List<Message> result = new ArrayList<>();
        for (Message message : messages) {
            result.add(message);
        }
        return result;
    }

    public interface CatalogMessages extends Messages {
        @Key("catalog.created")
        @DefaultMessage("Created {0}")
        @Description("Notification shown after creating an item")
        String createdNotification(String itemName);

        @Key("catalog.deleted")
        @DefaultMessage("Deleted {0}")
        @Description("Notification shown after deleting an item")
        String deletedNotification(String itemName);
    }
}
