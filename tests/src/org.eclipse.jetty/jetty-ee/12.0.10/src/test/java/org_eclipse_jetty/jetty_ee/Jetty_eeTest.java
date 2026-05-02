/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_ee;

import org.eclipse.jetty.ee.WebAppClassLoading;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.Attributes;
import org.eclipse.jetty.util.AttributesMap;
import org.eclipse.jetty.util.ClassMatcher;
import org.eclipse.jetty.util.component.Environment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Jetty_eeTest {
    @Test
    void defaultMatchersRecognizeProtectedAndHiddenJettyClassSpaces() {
        assertThat(WebAppClassLoading.PROTECTED_CLASSES_ATTRIBUTE)
                .isEqualTo("org.eclipse.jetty.webapp.systemClasses");
        assertThat(WebAppClassLoading.HIDDEN_CLASSES_ATTRIBUTE)
                .isEqualTo("org.eclipse.jetty.webapp.serverClasses");

        assertThat(WebAppClassLoading.DEFAULT_PROTECTED_CLASSES.match(String.class)).isTrue();
        assertThat(WebAppClassLoading.DEFAULT_PROTECTED_CLASSES.match("jakarta.servlet.Servlet")).isTrue();
        assertThat(WebAppClassLoading.DEFAULT_PROTECTED_CLASSES.match("org.xml.sax.SAXException")).isTrue();
        assertThat(WebAppClassLoading.DEFAULT_PROTECTED_CLASSES.match("com.example.Application")).isFalse();

        assertThat(WebAppClassLoading.DEFAULT_HIDDEN_CLASSES.match(WebAppClassLoading.class)).isTrue();
        assertThat(WebAppClassLoading.DEFAULT_HIDDEN_CLASSES.match("org.eclipse.jetty.server.Server")).isTrue();
        assertThat(WebAppClassLoading.DEFAULT_HIDDEN_CLASSES.match("com.example.Application")).isFalse();

        assertThat(new WebAppClassLoading()).isNotNull();
    }

    @Test
    void environmentAttributesWithStringArraysAreConvertedToReusableClassMatchers() {
        TestEnvironment attributes = new TestEnvironment("array-backed-environment");
        attributes.setAttribute(WebAppClassLoading.PROTECTED_CLASSES_ATTRIBUTE, new String[] {
                "com.acme.protected.",
                "-com.acme.protected.open."
        });
        attributes.setAttribute(WebAppClassLoading.HIDDEN_CLASSES_ATTRIBUTE, new String[] {
                "com.acme.internal.",
                "-com.acme.internal.public."
        });

        ClassMatcher protectedMatcher = WebAppClassLoading.getProtectedClasses(attributes);
        ClassMatcher hiddenMatcher = WebAppClassLoading.getHiddenClasses(attributes);

        assertThat(attributes.getAttribute(WebAppClassLoading.PROTECTED_CLASSES_ATTRIBUTE)).isSameAs(protectedMatcher);
        assertThat(WebAppClassLoading.getProtectedClasses(attributes)).isSameAs(protectedMatcher);
        assertThat(protectedMatcher.match("com.acme.protected.SecretService")).isTrue();
        assertThat(protectedMatcher.match("com.acme.protected.open.Controller")).isFalse();
        assertThat(protectedMatcher.match("com.acme.other.Component")).isFalse();

        assertThat(attributes.getAttribute(WebAppClassLoading.HIDDEN_CLASSES_ATTRIBUTE)).isSameAs(hiddenMatcher);
        assertThat(WebAppClassLoading.getHiddenClasses(attributes)).isSameAs(hiddenMatcher);
        assertThat(hiddenMatcher.match("com.acme.internal.DatabasePassword")).isTrue();
        assertThat(hiddenMatcher.match("com.acme.internal.public.StatusEndpoint")).isFalse();
        assertThat(hiddenMatcher.match("com.acme.public.Api")).isFalse();
    }

    @Test
    void existingEnvironmentAttributeMatchersAreReusedAndExtendedInPlace() {
        TestEnvironment attributes = new TestEnvironment("existing-matcher-environment");
        ClassMatcher existingProtectedMatcher = new ClassMatcher("com.example.base.");
        ClassMatcher existingHiddenMatcher = new ClassMatcher("com.example.secret.");
        attributes.setAttribute(WebAppClassLoading.PROTECTED_CLASSES_ATTRIBUTE, existingProtectedMatcher);
        attributes.setAttribute(WebAppClassLoading.HIDDEN_CLASSES_ATTRIBUTE, existingHiddenMatcher);

        WebAppClassLoading.addProtectedClasses((Environment) attributes, (String[]) null);
        WebAppClassLoading.addProtectedClasses((Environment) attributes);
        WebAppClassLoading.addProtectedClasses((Environment) attributes, "com.example.added.");
        WebAppClassLoading.addHiddenClasses((Environment) attributes, (String[]) null);
        WebAppClassLoading.addHiddenClasses((Environment) attributes);
        WebAppClassLoading.addHiddenClasses((Environment) attributes, "com.example.hidden.");

        assertThat(WebAppClassLoading.getProtectedClasses(attributes)).isSameAs(existingProtectedMatcher);
        assertThat(existingProtectedMatcher.match("com.example.base.Component")).isTrue();
        assertThat(existingProtectedMatcher.match("com.example.added.Component")).isTrue();
        assertThat(existingProtectedMatcher.match("com.example.secret.Component")).isFalse();

        assertThat(WebAppClassLoading.getHiddenClasses(attributes)).isSameAs(existingHiddenMatcher);
        assertThat(existingHiddenMatcher.match("com.example.secret.Component")).isTrue();
        assertThat(existingHiddenMatcher.match("com.example.hidden.Component")).isTrue();
        assertThat(existingHiddenMatcher.match("com.example.added.Component")).isFalse();
    }

    @Test
    void nullAndEmptyAdditionsDoNotCreateEnvironmentAttributeMatchers() {
        TestEnvironment attributes = new TestEnvironment("empty-additions-environment");

        WebAppClassLoading.addProtectedClasses((Environment) attributes, (String[]) null);
        WebAppClassLoading.addProtectedClasses((Environment) attributes);
        WebAppClassLoading.addHiddenClasses((Environment) attributes, (String[]) null);
        WebAppClassLoading.addHiddenClasses((Environment) attributes);

        assertThat(attributes.getAttribute(WebAppClassLoading.PROTECTED_CLASSES_ATTRIBUTE)).isNull();
        assertThat(attributes.getAttribute(WebAppClassLoading.HIDDEN_CLASSES_ATTRIBUTE)).isNull();
    }

    @Test
    void genericAttributesStoreProtectedClassMatcherWithoutEnvironmentDefaults() {
        Attributes attributes = new AttributesMap();

        WebAppClassLoading.addProtectedClasses(attributes, "com.attributes.protected.");

        ClassMatcher protectedMatcher = (ClassMatcher) attributes.getAttribute(
                WebAppClassLoading.PROTECTED_CLASSES_ATTRIBUTE);

        assertThat(protectedMatcher).isNotNull();
        assertThat(protectedMatcher.match("com.attributes.protected.Endpoint")).isTrue();
        assertThat(protectedMatcher.match("com.attributes.public.Endpoint")).isFalse();
        assertThat(protectedMatcher.match("java.lang.String")).isFalse();
        assertThat(attributes.getAttribute(WebAppClassLoading.HIDDEN_CLASSES_ATTRIBUTE)).isNull();
    }

    @Test
    void serverMatchersStartWithoutEnvironmentDefaultPatterns() {
        Server server = new Server();
        try {
            ClassMatcher protectedMatcher = WebAppClassLoading.getProtectedClasses(server);
            ClassMatcher hiddenMatcher = WebAppClassLoading.getHiddenClasses(server);

            assertThat(protectedMatcher.getPatterns()).isEmpty();
            assertThat(hiddenMatcher.getPatterns()).isEmpty();
            assertThat(protectedMatcher).isNotSameAs(WebAppClassLoading.DEFAULT_PROTECTED_CLASSES);
            assertThat(hiddenMatcher).isNotSameAs(WebAppClassLoading.DEFAULT_HIDDEN_CLASSES);
            assertThat(WebAppClassLoading.getProtectedClasses(server)).isSameAs(protectedMatcher);
            assertThat(WebAppClassLoading.getHiddenClasses(server)).isSameAs(hiddenMatcher);
        } finally {
            server.destroy();
        }
    }

    @Test
    void serverMatchersAreStoredAsIndependentServerAttributes() {
        Server server = new Server();
        try {
            ClassMatcher protectedMatcher = WebAppClassLoading.getProtectedClasses(server);
            ClassMatcher hiddenMatcher = WebAppClassLoading.getHiddenClasses(server);

            assertThat(protectedMatcher).isNotSameAs(hiddenMatcher);

            WebAppClassLoading.addProtectedClasses(server, "com.server.protected.");
            WebAppClassLoading.addHiddenClasses(server, "com.server.hidden.");

            assertThat(WebAppClassLoading.getProtectedClasses(server)).isSameAs(protectedMatcher);
            assertThat(WebAppClassLoading.getHiddenClasses(server)).isSameAs(hiddenMatcher);
            assertThat(server.getAttributeNameSet())
                    .contains(
                            WebAppClassLoading.PROTECTED_CLASSES_ATTRIBUTE,
                            WebAppClassLoading.HIDDEN_CLASSES_ATTRIBUTE);
            assertThat(protectedMatcher.match("com.server.protected.Endpoint")).isTrue();
            assertThat(protectedMatcher.match("com.server.hidden.Endpoint")).isFalse();
            assertThat(hiddenMatcher.match("com.server.hidden.Endpoint")).isTrue();
            assertThat(hiddenMatcher.match("com.server.protected.Endpoint")).isFalse();
        } finally {
            server.destroy();
        }
    }

    @Test
    void environmentMatchersStartAsCopiesOfDefaultsAndCanBeCustomized() {
        TestEnvironment environment = new TestEnvironment("custom-ee-environment");

        ClassMatcher protectedMatcher = WebAppClassLoading.getProtectedClasses(environment);
        ClassMatcher hiddenMatcher = WebAppClassLoading.getHiddenClasses(environment);

        assertThat(protectedMatcher).isNotSameAs(WebAppClassLoading.DEFAULT_PROTECTED_CLASSES);
        assertThat(hiddenMatcher).isNotSameAs(WebAppClassLoading.DEFAULT_HIDDEN_CLASSES);
        assertThat(protectedMatcher.match("java.lang.String")).isTrue();
        assertThat(protectedMatcher.match("jakarta.servlet.Servlet")).isTrue();
        assertThat(hiddenMatcher.match("org.eclipse.jetty.ee.WebAppClassLoading")).isTrue();

        WebAppClassLoading.addProtectedClasses((Environment) environment, "com.environment.protected.");
        WebAppClassLoading.addHiddenClasses((Environment) environment, "com.environment.hidden.");

        assertThat(WebAppClassLoading.getProtectedClasses(environment)).isSameAs(protectedMatcher);
        assertThat(WebAppClassLoading.getHiddenClasses(environment)).isSameAs(hiddenMatcher);
        assertThat(protectedMatcher.match("com.environment.protected.Model")).isTrue();
        assertThat(hiddenMatcher.match("com.environment.hidden.Model")).isTrue();
        assertThat(WebAppClassLoading.DEFAULT_PROTECTED_CLASSES.match("com.environment.protected.Model")).isFalse();
        assertThat(WebAppClassLoading.DEFAULT_HIDDEN_CLASSES.match("com.environment.hidden.Model")).isFalse();
    }

    @Test
    void staticAdditionsUpdateDefaultMatchersAndFutureEnvironmentCopies() {
        String protectedPattern = "org.eclipse.jetty.ee.test.default.protected.";
        String hiddenPattern = "org.eclipse.jetty.ee.test.default.hidden.";
        try {
            WebAppClassLoading.addProtectedClasses(protectedPattern);
            WebAppClassLoading.addHiddenClasses(hiddenPattern);

            assertThat(WebAppClassLoading.DEFAULT_PROTECTED_CLASSES.match(protectedPattern + "Component")).isTrue();
            assertThat(WebAppClassLoading.DEFAULT_HIDDEN_CLASSES.match(hiddenPattern + "Component")).isTrue();

            TestEnvironment environment = new TestEnvironment("environment-after-default-additions");
            assertThat(WebAppClassLoading.getProtectedClasses(environment).match(protectedPattern + "Component"))
                    .isTrue();
            assertThat(WebAppClassLoading.getHiddenClasses(environment).match(hiddenPattern + "Component"))
                    .isTrue();
        } finally {
            WebAppClassLoading.DEFAULT_PROTECTED_CLASSES.remove(protectedPattern);
            WebAppClassLoading.DEFAULT_HIDDEN_CLASSES.remove(hiddenPattern);
        }
    }

    private static final class TestEnvironment extends AttributesMap implements Environment {
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
    }
}
