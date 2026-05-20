/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_mail.jakarta_mail_api;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimePart;
import java.net.URL;
import java.net.URLClassLoader;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MimeUtilTest {
    private static final String CONTENT_TYPE_HANDLER_PROPERTY = "mail.mime.contenttypehandler";
    private static final String ORIGINAL_CONTENT_TYPE = "text/plain; charset=unknown";
    private static final String CLEANED_CONTENT_TYPE = "text/plain; charset=UTF-8";

    @Test
    void getContentTypeUsesHandlerLoadedByContextClassLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        String originalProperty = System.getProperty(CONTENT_TYPE_HANDLER_PROPERTY);

        try {
            System.setProperty(CONTENT_TYPE_HANDLER_PROPERTY, ContentTypeHandler.class.getName());
            thread.setContextClassLoader(MimeUtilTest.class.getClassLoader());

            MimeBodyPart bodyPart = new MimeBodyPart();
            bodyPart.setHeader("Content-Type", ORIGINAL_CONTENT_TYPE);

            assertEquals(CLEANED_CONTENT_TYPE, bodyPart.getContentType());
        } finally {
            restoreSystemProperty(originalProperty);
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    @Test
    void getContentTypeFallsBackToMimeUtilDefiningClassLoaderAfterContextLoaderMiss() throws Exception {
        try {
            runInIsolatedMailClassLoader();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static void runInIsolatedMailClassLoader() throws Exception {
        URL mailApiLocation = MimeBodyPart.class.getProtectionDomain().getCodeSource().getLocation();
        URL testClassesLocation = MimeUtilTest.class.getProtectionDomain().getCodeSource().getLocation();

        try (ChildFirstUrlClassLoader classLoader = new ChildFirstUrlClassLoader(
                new URL[] { mailApiLocation, testClassesLocation },
                MimeUtilTest.class.getClassLoader())) {
            Runnable scenario = classLoader
                    .loadClass(IsolatedFallbackScenario.class.getName())
                    .asSubclass(Runnable.class)
                    .getDeclaredConstructor()
                    .newInstance();
            scenario.run();
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

    public static final class IsolatedFallbackScenario implements Runnable {
        @Override
        public void run() {
            Thread thread = Thread.currentThread();
            ClassLoader originalContextClassLoader = thread.getContextClassLoader();
            String originalProperty = System.getProperty(CONTENT_TYPE_HANDLER_PROPERTY);

            try {
                System.setProperty(CONTENT_TYPE_HANDLER_PROPERTY, ContentTypeHandler.class.getName());
                thread.setContextClassLoader(new ClassHidingClassLoader(
                        IsolatedFallbackScenario.class.getClassLoader(),
                        ContentTypeHandler.class.getName()));

                Session session = null;
                MimeMessage message = new MimeMessage(session);
                message.setHeader("Content-Type", ORIGINAL_CONTENT_TYPE);

                String actualContentType = message.getContentType();
                if (!CLEANED_CONTENT_TYPE.equals(actualContentType)) {
                    throw new AssertionError("Unexpected Content-Type: " + actualContentType);
                }
            } catch (Exception exception) {
                throw new AssertionError(exception);
            } finally {
                restoreSystemProperty(originalProperty);
                thread.setContextClassLoader(originalContextClassLoader);
            }
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

    private static final class ChildFirstUrlClassLoader extends URLClassLoader {
        private ChildFirstUrlClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> loadedClass = findLoadedClass(name);
                if (loadedClass == null && isChildFirstClass(name)) {
                    try {
                        loadedClass = findClass(name);
                    } catch (ClassNotFoundException exception) {
                        loadedClass = super.loadClass(name, false);
                    }
                }
                if (loadedClass == null) {
                    loadedClass = super.loadClass(name, false);
                }
                if (resolve) {
                    resolveClass(loadedClass);
                }
                return loadedClass;
            }
        }

        private static boolean isChildFirstClass(String className) {
            return className.startsWith("jakarta.mail.")
                    || className.equals(MimeUtilTest.class.getName())
                    || className.startsWith(MimeUtilTest.class.getName() + "$");
        }
    }
}
