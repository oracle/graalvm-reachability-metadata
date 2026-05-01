/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_ee;

import java.util.Set;

import org.eclipse.jetty.ee.WebAppClassLoading;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Attributes;
import org.eclipse.jetty.util.ClassMatcher;
import org.eclipse.jetty.util.component.Environment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Jetty_eeTest {
    private static final String PROTECTED_ATTRIBUTE = WebAppClassLoading.PROTECTED_CLASSES_ATTRIBUTE;
    private static final String HIDDEN_ATTRIBUTE = WebAppClassLoading.HIDDEN_CLASSES_ATTRIBUTE;

    @Test
    void constructorCreatesInstance() {
        assertThat(new WebAppClassLoading()).isNotNull();
    }

    @Test
    void defaultProtectedClassesCoverStandardApiPackages() {
        ClassMatcher matcher = WebAppClassLoading.DEFAULT_PROTECTED_CLASSES;

        assertThat(matcher.match(String.class.getName())).isTrue();
        assertThat(matcher.match("javax.naming.InitialContext")).isTrue();
        assertThat(matcher.match("jakarta.servlet.Servlet")).isTrue();
        assertThat(matcher.match("org.xml.sax.XMLReader")).isTrue();
        assertThat(matcher.match("org.w3c.dom.Document")).isTrue();
        assertThat(matcher.match("com.acme.Application")).isFalse();
    }

    @Test
    void defaultHiddenClassesCoverJettyImplementationPackagesOnly() {
        ClassMatcher matcher = WebAppClassLoading.DEFAULT_HIDDEN_CLASSES;

        assertThat(matcher.match(WebAppClassLoading.class.getName())).isTrue();
        assertThat(matcher.match("org.eclipse.jetty.server.Server")).isTrue();
        assertThat(matcher.match("java.lang.String")).isFalse();
        assertThat(matcher.match("com.acme.Application")).isFalse();
    }

    @Test
    void serverCreatesAndReusesProtectedMatcherFromStringArrayAttribute() {
        Server server = new Server();
        String[] patterns = {"com.example.api.", "-com.example.api.internal."};
        server.setAttribute(PROTECTED_ATTRIBUTE, patterns);

        ClassMatcher matcher = WebAppClassLoading.getProtectedClasses(server);
        ClassMatcher reused = WebAppClassLoading.getProtectedClasses(server);

        assertThat(reused).isSameAs(matcher);
        assertThat(server.getAttribute(PROTECTED_ATTRIBUTE)).isSameAs(matcher);
        assertThat(matcher.getPatterns()).containsExactlyInAnyOrder(patterns);
        assertThat(matcher.getInclusions()).containsExactly("com.example.api.");
        assertThat(matcher.getExclusions()).containsExactly("com.example.api.internal.");
        assertThat(matcher.match("com.example.api.PublicType")).isTrue();
        assertThat(matcher.match("com.example.api.internal.PrivateType")).isFalse();
    }

    @Test
    void serverCreatesAndReusesHiddenMatcherFromStringArrayAttribute() {
        Server server = new Server();
        String[] patterns = {"com.example.server.", "-com.example.server.public."};
        server.setAttribute(HIDDEN_ATTRIBUTE, patterns);

        ClassMatcher matcher = WebAppClassLoading.getHiddenClasses(server);
        ClassMatcher reused = WebAppClassLoading.getHiddenClasses(server);

        assertThat(reused).isSameAs(matcher);
        assertThat(server.getAttribute(HIDDEN_ATTRIBUTE)).isSameAs(matcher);
        assertThat(matcher.getPatterns()).containsExactlyInAnyOrder(patterns);
        assertThat(matcher.match("com.example.server.SecretHandler")).isTrue();
        assertThat(matcher.match("com.example.server.public.Endpoint")).isFalse();
    }

    @Test
    void serverReturnsExistingClassMatcherWithoutCopying() {
        Server server = new Server();
        ClassMatcher protectedMatcher = new ClassMatcher("org.example.protected.");
        ClassMatcher hiddenMatcher = new ClassMatcher("org.example.hidden.");
        server.setAttribute(PROTECTED_ATTRIBUTE, protectedMatcher);
        server.setAttribute(HIDDEN_ATTRIBUTE, hiddenMatcher);

        assertThat(WebAppClassLoading.getProtectedClasses(server)).isSameAs(protectedMatcher);
        assertThat(WebAppClassLoading.getHiddenClasses(server)).isSameAs(hiddenMatcher);
    }

    @Test
    void serverAddersCreateIndependentMatchersAndIgnoreEmptyInputs() {
        Server server = new Server();

        WebAppClassLoading.addProtectedClasses(server, "org.example.protected.", "org.example.ProtectedType");
        WebAppClassLoading.addHiddenClasses(server, "org.example.hidden.", "org.example.HiddenType");
        WebAppClassLoading.addProtectedClasses(server);
        WebAppClassLoading.addHiddenClasses(server, (String[]) null);

        ClassMatcher protectedMatcher = WebAppClassLoading.getProtectedClasses(server);
        ClassMatcher hiddenMatcher = WebAppClassLoading.getHiddenClasses(server);
        assertThat(protectedMatcher).isNotSameAs(hiddenMatcher);
        assertThat(protectedMatcher.match("org.example.protected.Service")).isTrue();
        assertThat(protectedMatcher.match("org.example.ProtectedType")).isTrue();
        assertThat(protectedMatcher.match("org.example.hidden.Secret")).isFalse();
        assertThat(hiddenMatcher.match("org.example.hidden.Secret")).isTrue();
        assertThat(hiddenMatcher.match("org.example.HiddenType")).isTrue();
        assertThat(hiddenMatcher.match("org.example.protected.Service")).isFalse();
    }

    @Test
    void noOpServerAddersDoNotCreateAttributes() {
        Server server = new Server();

        WebAppClassLoading.addProtectedClasses(server);
        WebAppClassLoading.addProtectedClasses(server, (String[]) null);
        WebAppClassLoading.addHiddenClasses(server);
        WebAppClassLoading.addHiddenClasses(server, (String[]) null);

        assertThat(server.getAttributeNameSet()).isEmpty();
    }

    @Test
    void serverGettersStartWithoutDefaultsAndServerAddersReuseStoredMatchers() {
        Server server = new Server();

        ClassMatcher initialProtected = WebAppClassLoading.getProtectedClasses(server);
        ClassMatcher initialHidden = WebAppClassLoading.getHiddenClasses(server);
        WebAppClassLoading.addProtectedClasses(server, "server.protected.");
        WebAppClassLoading.addHiddenClasses(server, "server.hidden.");

        assertThat(initialProtected).isSameAs(server.getAttribute(PROTECTED_ATTRIBUTE));
        assertThat(initialHidden).isSameAs(server.getAttribute(HIDDEN_ATTRIBUTE));
        assertThat(WebAppClassLoading.getProtectedClasses(server)).isSameAs(initialProtected);
        assertThat(WebAppClassLoading.getHiddenClasses(server)).isSameAs(initialHidden);
        assertThat(initialProtected.match("server.protected.Component")).isTrue();
        assertThat(initialProtected.match("java.lang.String")).isFalse();
        assertThat(initialHidden.match("server.hidden.Component")).isTrue();
        assertThat(initialHidden.match(WebAppClassLoading.class.getName())).isFalse();
    }

    @Test
    void environmentGettersCopyDefaultMatchersAndReuseEnvironmentAttributes() {
        TestEnvironment environment = new TestEnvironment("ee-test-environment");

        ClassMatcher protectedMatcher = WebAppClassLoading.getProtectedClasses(environment);
        ClassMatcher hiddenMatcher = WebAppClassLoading.getHiddenClasses(environment);

        assertThat(protectedMatcher).isSameAs(environment.getAttribute(PROTECTED_ATTRIBUTE));
        assertThat(hiddenMatcher).isSameAs(environment.getAttribute(HIDDEN_ATTRIBUTE));
        assertThat(WebAppClassLoading.getProtectedClasses(environment)).isSameAs(protectedMatcher);
        assertThat(WebAppClassLoading.getHiddenClasses(environment)).isSameAs(hiddenMatcher);
        assertThat(protectedMatcher).isNotSameAs(WebAppClassLoading.DEFAULT_PROTECTED_CLASSES);
        assertThat(hiddenMatcher).isNotSameAs(WebAppClassLoading.DEFAULT_HIDDEN_CLASSES);
        assertThat(protectedMatcher.match(String.class.getName())).isTrue();
        assertThat(hiddenMatcher.match(WebAppClassLoading.class.getName())).isTrue();
    }

    @Test
    void environmentAddersAugmentCopiedDefaults() {
        TestEnvironment environment = new TestEnvironment("ee-adders-test-environment");

        WebAppClassLoading.addProtectedClasses(environment, "environment.protected.");
        WebAppClassLoading.addHiddenClasses(environment, "environment.hidden.");

        ClassMatcher protectedMatcher = WebAppClassLoading.getProtectedClasses(environment);
        ClassMatcher hiddenMatcher = WebAppClassLoading.getHiddenClasses(environment);
        assertThat(protectedMatcher.match(String.class.getName())).isTrue();
        assertThat(protectedMatcher.match("environment.protected.Api")).isTrue();
        assertThat(protectedMatcher.match("environment.hidden.Secret")).isFalse();
        assertThat(hiddenMatcher.match(WebAppClassLoading.class.getName())).isTrue();
        assertThat(hiddenMatcher.match("environment.hidden.Secret")).isTrue();
        assertThat(hiddenMatcher.match("environment.protected.Api")).isFalse();
    }

    @Test
    void globalAddersAugmentDefaultMatchers() {
        String protectedPattern = "global.protected.jetty.ee.";
        String hiddenPattern = "global.hidden.jetty.ee.";

        WebAppClassLoading.addProtectedClasses(protectedPattern);
        WebAppClassLoading.addHiddenClasses(hiddenPattern);

        assertThat(WebAppClassLoading.DEFAULT_PROTECTED_CLASSES.match("global.protected.jetty.ee.Type")).isTrue();
        assertThat(WebAppClassLoading.DEFAULT_HIDDEN_CLASSES.match("global.hidden.jetty.ee.Type")).isTrue();
    }

    private static final class TestEnvironment extends Attributes.Mapped implements Environment {
        private final String name;
        private final ClassLoader classLoader;

        private TestEnvironment(String name) {
            this.name = name;
            this.classLoader = Jetty_eeTest.class.getClassLoader();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoader;
        }

        @Override
        public Set<String> getAttributeNameSet() {
            return super.getAttributeNameSet();
        }
    }
}
