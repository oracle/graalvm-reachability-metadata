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

import static org.assertj.core.api.Assertions.assertThat;

public class MimeUtilTest {

    private static final String CONTENT_TYPE_HANDLER_PROPERTY = "mail.mime.contenttypehandler";

    @Test
    void cleansMimeBodyPartContentTypeUsingConfiguredHandler() throws Exception {
        withSystemProperty(CONTENT_TYPE_HANDLER_PROPERTY, ContentTypeHandler.class.getName(), () -> {
            MimeBodyPart bodyPart = new MimeBodyPart();
            bodyPart.setHeader("Content-Type", "application/x-unknown; x-raw=true");

            String contentType = withContextClassLoader(classLoaderWithoutHandlerVisibility(),
                    bodyPart::getContentType);

            assertThat(contentType).isEqualTo("text/plain; charset=UTF-8");
            assertThat(ContentTypeHandler.lastMimePart).isSameAs(bodyPart);
            assertThat(ContentTypeHandler.lastContentType).isEqualTo("application/x-unknown; x-raw=true");
        });
    }

    private static ClassLoader classLoaderWithoutHandlerVisibility() {
        return new ClassLoader(null) {
        };
    }

    private static void withSystemProperty(String property, String value, ThrowingRunnable runnable) throws Exception {
        String originalValue = System.getProperty(property);
        boolean hadOriginalValue = System.getProperties().containsKey(property);
        System.setProperty(property, value);
        try {
            runnable.run();
        } finally {
            restoreSystemProperty(property, originalValue, hadOriginalValue);
        }
    }

    private static void restoreSystemProperty(String property, String originalValue, boolean hadOriginalValue) {
        if (hadOriginalValue) {
            System.setProperty(property, originalValue);
        } else {
            System.clearProperty(property);
        }
    }

    private static <T> T withContextClassLoader(
            ClassLoader classLoader,
            ThrowingSupplier<T> supplier) throws Exception {
        Thread currentThread = Thread.currentThread();
        ClassLoader originalClassLoader = currentThread.getContextClassLoader();
        currentThread.setContextClassLoader(classLoader);
        try {
            return supplier.get();
        } finally {
            currentThread.setContextClassLoader(originalClassLoader);
        }
    }

    public static final class ContentTypeHandler {

        private static MimePart lastMimePart;
        private static String lastContentType;

        public static String cleanContentType(MimePart mimePart, String contentType) {
            lastMimePart = mimePart;
            lastContentType = contentType;
            return "text/plain; charset=UTF-8";
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
