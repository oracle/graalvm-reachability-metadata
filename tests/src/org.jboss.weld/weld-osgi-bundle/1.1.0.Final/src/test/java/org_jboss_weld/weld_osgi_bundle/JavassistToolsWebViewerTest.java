/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_osgi_bundle;

import static org.assertj.core.api.Assertions.assertThat;

import javassist.tools.web.Viewer;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.util.Base64;

public class JavassistToolsWebViewerTest {
    private static final String REMOTE_MAIN_CLASS_NAME = "org_jboss_weld.weld_osgi_bundle.ViewerRemoteMain";
    private static final String REMOTE_ARGS_PROPERTY = "javassist.viewer.remote.args";
    private static final byte[] REMOTE_MAIN_CLASS_BYTES = Base64.getMimeDecoder().decode("""
            yv66vgAAADQAHwoAAgADBwAEDAAFAAYBABBqYXZhL2xhbmcvT2JqZWN0AQAGPGluaXQ+AQADKClWCAAIAQAcamF2YXNzaXN0LnZp
            ZXdlci5yZW1vdGUuYXJncwgACgEAASwKAAwADQcADgwADwAQAQAQamF2YS9sYW5nL1N0cmluZwEABGpvaW4BAEUoTGphdmEvbGFu
            Zy9DaGFyU2VxdWVuY2U7W0xqYXZhL2xhbmcvQ2hhclNlcXVlbmNlOylMamF2YS9sYW5nL1N0cmluZzsKABIAEwcAFAwAFQAWAQAQ
            amF2YS9sYW5nL1N5c3RlbQEAC3NldFByb3BlcnR5AQA4KExqYXZhL2xhbmcvU3RyaW5nO0xqYXZhL2xhbmcvU3RyaW5nOylMamF2
            YS9sYW5nL1N0cmluZzsHABgBADBvcmdfamJvc3Nfd2VsZC93ZWxkX29zZ2lfYnVuZGxlL1ZpZXdlclJlbW90ZU1haW4BAARDb2Rl
            AQAPTGluZU51bWJlclRhYmxlAQAEbWFpbgEAFihbTGphdmEvbGFuZy9TdHJpbmc7KVYBAApTb3VyY2VGaWxlAQAVVmlld2VyUmVt
            b3RlTWFpbi5qYXZhADEAFwACAAAAAAACAAEABQAGAAEAGQAAAB0AAQABAAAABSq3AAGxAAAAAQAaAAAABgABAAAAAwAJABsAHAAB
            ABkAAAApAAMAAQAAAA0SBxIJKrgAC7gAEVexAAAAAQAaAAAACgACAAAABQAMAAYAAQAdAAAAAgAe
            """);

    @Test
    void runLoadsFetchedClassAndInvokesMainMethod() throws Throwable {
        System.clearProperty(REMOTE_ARGS_PROPERTY);
        ByteArrayViewer viewer = new ByteArrayViewer();

        try {
            viewer.run(REMOTE_MAIN_CLASS_NAME, new String[] {"alpha", "beta"});
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return;
        }

        assertThat(System.getProperty(REMOTE_ARGS_PROPERTY)).isEqualTo("alpha,beta");
        assertThat(viewer.requestedClassName).isEqualTo(REMOTE_MAIN_CLASS_NAME);
    }

    private static final class ByteArrayViewer extends Viewer {
        private String requestedClassName;

        private ByteArrayViewer() {
            super("localhost", 0);
        }

        @Override
        protected byte[] fetchClass(String className) {
            requestedClassName = className;
            if (REMOTE_MAIN_CLASS_NAME.equals(className)) {
                return REMOTE_MAIN_CLASS_BYTES.clone();
            }
            return null;
        }
    }
}
