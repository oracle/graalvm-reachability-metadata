/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_mail.jakarta_mail_api;

import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimePart;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MimeUtilTest {
    private static final String CONTENT_TYPE_HANDLER_PROPERTY = "mail.mime.contenttypehandler";
    private static final String ORIGINAL_CONTENT_TYPE = "text/plain; charset=unknown";
    private static final String CLEANED_CONTENT_TYPE = "text/plain; charset=UTF-8";

    @Test
    void getContentTypeUsesConfiguredContentTypeHandlerAfterContextLoaderMiss() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        String originalProperty = System.getProperty(CONTENT_TYPE_HANDLER_PROPERTY);
        String handlerClassName = ContentTypeHandler.class.getName();

        try {
            System.setProperty(CONTENT_TYPE_HANDLER_PROPERTY, handlerClassName);
            thread.setContextClassLoader(new ClassHidingClassLoader(
                    MimeUtilTest.class.getClassLoader(),
                    handlerClassName));

            MimeBodyPart bodyPart = new MimeBodyPart();
            bodyPart.setHeader("Content-Type", ORIGINAL_CONTENT_TYPE);

            assertEquals(CLEANED_CONTENT_TYPE, bodyPart.getContentType());
        } finally {
            restoreSystemProperty(originalProperty);
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    private static void restoreSystemProperty(String originalProperty) {
        if (originalProperty == null) {
            System.clearProperty(CONTENT_TYPE_HANDLER_PROPERTY);
        } else {
            System.setProperty(CONTENT_TYPE_HANDLER_PROPERTY, originalProperty);
        }
    }

    public static final class ContentTypeHandler {
        public static String cleanContentType(MimePart part, String contentType) {
            if (ORIGINAL_CONTENT_TYPE.equals(contentType)) {
                return CLEANED_CONTENT_TYPE;
            }
            return contentType;
        }
    }

    private static final class ClassHidingClassLoader extends ClassLoader {
        private final String hiddenClassName;

        private ClassHidingClassLoader(ClassLoader parent, String hiddenClassName) {
            super(parent);
            this.hiddenClassName = hiddenClassName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (hiddenClassName.equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
