/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.i18n.client.LocalizableResource.GenerateKeys;
import com.google.gwt.i18n.server.KeyGenerator;
import com.google.gwt.i18n.server.KeyGeneratorAdapter;
import com.google.gwt.i18n.server.Message;
import com.google.gwt.i18n.server.Message.AlternateFormMapping;
import com.google.gwt.i18n.server.MessageFormatUtils.MessageStyle;
import com.google.gwt.i18n.server.MessageInterface;
import com.google.gwt.i18n.server.MessageInterfaceVisitor;
import com.google.gwt.i18n.server.MessageProcessingException;
import com.google.gwt.i18n.server.MessageTranslation;
import com.google.gwt.i18n.server.MessageUtils;
import com.google.gwt.i18n.server.MessageVisitor;
import com.google.gwt.i18n.server.Parameter;
import com.google.gwt.i18n.server.Type;
import com.google.gwt.i18n.server.keygen.FullyQualifiedMethodNameKeyGenerator;
import com.google.gwt.i18n.shared.GwtLocale;

import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

public class MessageUtilsTest {
    private static final String QUALIFIED_MESSAGE_INTERFACE =
            "org_gwtproject.gwt_user.CatalogMessages";
    private static final String MESSAGE_METHOD = "savedNotification";
    private static final TestMessage MESSAGE = new TestMessage();

    @Test
    public void createsServerKeyGeneratorFromGenerateKeysAnnotation()
            throws MessageUtils.KeyGeneratorException {
        GenerateKeys generateKeys = ServerKeyMessages.class.getAnnotation(GenerateKeys.class);

        KeyGenerator keyGenerator = MessageUtils.getKeyGenerator(generateKeys);

        assertThat(keyGenerator).isInstanceOf(FullyQualifiedMethodNameKeyGenerator.class);
        assertThat(keyGenerator.generateKey(MESSAGE))
                .isEqualTo(QUALIFIED_MESSAGE_INTERFACE + "." + MESSAGE_METHOD);
    }

    @Test
    public void adaptsDeprecatedRebindKeyGeneratorFromGenerateKeysAnnotation()
            throws MessageUtils.KeyGeneratorException {
        GenerateKeys generateKeys = RebindKeyMessages.class.getAnnotation(GenerateKeys.class);

        KeyGenerator keyGenerator = MessageUtils.getKeyGenerator(generateKeys);

        assertThat(keyGenerator).isInstanceOf(KeyGeneratorAdapter.class);
        assertThat(keyGenerator).hasToString(
                KeyGeneratorAdapter.class.getName() + " for "
                        + "com.google.gwt.i18n.rebind.keygen.FullyQualifiedMethodNameKeyGenerator");
        assertThat(keyGenerator.generateKey(MESSAGE))
                .isEqualTo(QUALIFIED_MESSAGE_INTERFACE + "." + MESSAGE_METHOD);
    }

    @GenerateKeys("com.google.gwt.i18n.server.keygen.FullyQualifiedMethodNameKeyGenerator")
    private interface ServerKeyMessages {
    }

    @GenerateKeys("com.google.gwt.i18n.rebind.keygen.FullyQualifiedMethodNameKeyGenerator")
    private interface RebindKeyMessages {
    }

    private static final class TestMessage implements Message {
        private static final TestMessageInterface MESSAGE_INTERFACE = new TestMessageInterface();

        @Override
        public void accept(MessageVisitor visitor) throws MessageProcessingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void accept(MessageVisitor visitor, GwtLocale locale) throws MessageProcessingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public int compareTo(Message other) {
            return getKey().compareTo(other.getKey());
        }

        @Override
        public Iterable<AlternateFormMapping> getAllMessageForms() {
            return Collections.emptyList();
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
            return null;
        }

        @Override
        public String getDefaultMessage() {
            return "Saved {0}";
        }

        @Override
        public String getDescription() {
            return "Notification shown after saving";
        }

        @Override
        public String getKey() {
            return MESSAGE_METHOD;
        }

        @Override
        public GwtLocale getMatchedLocale() {
            return null;
        }

        @Override
        public String getMeaning() {
            return "file operation";
        }

        @Override
        public MessageInterface getMessageInterface() {
            return MESSAGE_INTERFACE;
        }

        @Override
        public MessageStyle getMessageStyle() {
            return MessageStyle.MESSAGE_FORMAT;
        }

        @Override
        public String getMethodName() {
            return MESSAGE_METHOD;
        }

        @Override
        public List<Parameter> getParameters() {
            return Collections.emptyList();
        }

        @Override
        public Type getReturnType() {
            return Type.STRING;
        }

        @Override
        public int[] getSelectorParameterIndices() {
            return new int[0];
        }

        @Override
        public MessageTranslation getTranslation(GwtLocale locale) {
            return this;
        }

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
            return false;
        }

        @Override
        public boolean isVarArgs() {
            return false;
        }
    }

    private static final class TestMessageInterface implements MessageInterface {
        @Override
        public void accept(MessageInterfaceVisitor visitor) throws MessageProcessingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void accept(MessageInterfaceVisitor visitor, GwtLocale locale)
                throws MessageProcessingException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
            return null;
        }

        @Override
        public String getClassName() {
            return "CatalogMessages";
        }

        @Override
        public String getPackageName() {
            return "org_gwtproject.gwt_user";
        }

        @Override
        public String getQualifiedName() {
            return QUALIFIED_MESSAGE_INTERFACE;
        }

        @Override
        public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
            return false;
        }
    }
}
