/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_validation.validation_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;

import javax.validation.Configuration;
import javax.validation.ConstraintValidatorFactory;
import javax.validation.MessageInterpolator;
import javax.validation.TraversableResolver;
import javax.validation.Validation;
import javax.validation.ValidationException;
import javax.validation.Validator;
import javax.validation.ValidatorContext;
import javax.validation.ValidatorFactory;
import javax.validation.spi.BootstrapState;
import javax.validation.spi.ConfigurationState;
import javax.validation.spi.ValidationProvider;
import org.junit.jupiter.api.Test;

public class ValidationInnerDefaultValidationProviderResolverTest {

    private static final String SERVICES_FILE = "META-INF/services/" + ValidationProvider.class.getName();

    @Test
    void defaultResolverLoadsProviderWithContextClassLoader() throws Exception {
        Path providerDefinition = writeProviderDefinition();
        ClassLoader providerClassLoader = new ProviderDefinitionClassLoader(
                parentClassLoader(),
                providerDefinition.toUri().toURL()
        );

        try {
            TestValidationConfiguration configuration = configureWith(providerClassLoader);

            assertDiscoveredProvider(configuration);
        } finally {
            Files.deleteIfExists(providerDefinition);
        }
    }

    @Test
    void defaultResolverDiscoversProviderFromContextClassLoaderAndFallsBackToCallerClassLoader() throws Exception {
        Path providerDefinition = writeProviderDefinition();
        ClassLoader providerClassLoader = new FailingProviderDefinitionClassLoader(
                parentClassLoader(),
                providerDefinition.toUri().toURL()
        );

        try {
            TestValidationConfiguration configuration = configureWith(providerClassLoader);

            assertDiscoveredProvider(configuration);
        } finally {
            Files.deleteIfExists(providerDefinition);
        }
    }

    private ClassLoader parentClassLoader() {
        ClassLoader parentClassLoader = Thread.currentThread().getContextClassLoader();
        if (parentClassLoader == null) {
            return getClass().getClassLoader();
        }
        return parentClassLoader;
    }

    private static TestValidationConfiguration configureWith(ClassLoader providerClassLoader) {
        TestValidationProvider.reset();
        ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(providerClassLoader);
            return (TestValidationConfiguration) Validation.byDefaultProvider().configure();
        } finally {
            Thread.currentThread().setContextClassLoader(originalContextClassLoader);
        }
    }

    private static void assertDiscoveredProvider(TestValidationConfiguration configuration) {
        assertThat(TestValidationProvider.instantiationCount).isEqualTo(1);
        assertThat(TestValidationProvider.lastInstance).isNotNull();
        assertThat(configuration.provider()).isSameAs(TestValidationProvider.lastInstance);
        assertThat(configuration.bootstrapState()).isNotNull();
    }

    private static Path writeProviderDefinition() throws IOException {
        Path providerDefinition = Files.createTempFile("validation-provider", ".services");
        Files.write(
                providerDefinition,
                Collections.singleton(TestValidationProvider.class.getName()),
                StandardCharsets.UTF_8
        );
        return providerDefinition;
    }

    private static class ProviderDefinitionClassLoader extends ClassLoader {

        private final URL providerDefinition;

        private ProviderDefinitionClassLoader(ClassLoader parent, URL providerDefinition) {
            super(parent);
            this.providerDefinition = providerDefinition;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (SERVICES_FILE.equals(name)) {
                return Collections.enumeration(Collections.singleton(providerDefinition));
            }
            return super.getResources(name);
        }
    }

    private static final class FailingProviderDefinitionClassLoader extends ProviderDefinitionClassLoader {

        private FailingProviderDefinitionClassLoader(ClassLoader parent, URL providerDefinition) {
            super(parent, providerDefinition);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (TestValidationProvider.class.getName().equals(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name);
        }
    }

    public interface TestValidationConfiguration extends Configuration<TestValidationConfiguration> {

        TestValidationProvider provider();

        BootstrapState bootstrapState();
    }

    public static final class TestValidationProvider implements ValidationProvider<TestValidationConfiguration> {

        private static int instantiationCount;
        private static TestValidationProvider lastInstance;

        public TestValidationProvider() {
            instantiationCount++;
            lastInstance = this;
        }

        static void reset() {
            instantiationCount = 0;
            lastInstance = null;
        }

        @Override
        public TestValidationConfiguration createSpecializedConfiguration(BootstrapState state) {
            return new TestValidationConfigurationImpl(this, state);
        }

        @Override
        public Configuration<?> createGenericConfiguration(BootstrapState state) {
            return new TestValidationConfigurationImpl(this, state);
        }

        @Override
        public ValidatorFactory buildValidatorFactory(ConfigurationState configurationState) {
            return new TestValidatorFactory();
        }
    }

    private static final class TestValidationConfigurationImpl implements TestValidationConfiguration {

        private final TestValidationProvider provider;
        private final BootstrapState bootstrapState;

        private TestValidationConfigurationImpl(TestValidationProvider provider, BootstrapState bootstrapState) {
            this.provider = provider;
            this.bootstrapState = bootstrapState;
        }

        @Override
        public TestValidationProvider provider() {
            return provider;
        }

        @Override
        public BootstrapState bootstrapState() {
            return bootstrapState;
        }

        @Override
        public TestValidationConfiguration ignoreXmlConfiguration() {
            return this;
        }

        @Override
        public TestValidationConfiguration messageInterpolator(MessageInterpolator interpolator) {
            return this;
        }

        @Override
        public TestValidationConfiguration traversableResolver(TraversableResolver resolver) {
            return this;
        }

        @Override
        public TestValidationConfiguration constraintValidatorFactory(
                ConstraintValidatorFactory constraintValidatorFactory
        ) {
            return this;
        }

        @Override
        public TestValidationConfiguration addMapping(InputStream stream) {
            return this;
        }

        @Override
        public TestValidationConfiguration addProperty(String name, String value) {
            return this;
        }

        @Override
        public MessageInterpolator getDefaultMessageInterpolator() {
            return null;
        }

        @Override
        public TraversableResolver getDefaultTraversableResolver() {
            return null;
        }

        @Override
        public ConstraintValidatorFactory getDefaultConstraintValidatorFactory() {
            return null;
        }

        @Override
        public ValidatorFactory buildValidatorFactory() {
            return new TestValidatorFactory();
        }
    }

    private static final class TestValidatorFactory implements ValidatorFactory {

        @Override
        public Validator getValidator() {
            return null;
        }

        @Override
        public ValidatorContext usingContext() {
            return null;
        }

        @Override
        public MessageInterpolator getMessageInterpolator() {
            return null;
        }

        @Override
        public TraversableResolver getTraversableResolver() {
            return null;
        }

        @Override
        public ConstraintValidatorFactory getConstraintValidatorFactory() {
            return null;
        }

        @Override
        public <T> T unwrap(Class<T> type) {
            throw new ValidationException("No provider-specific API available for " + type.getName());
        }
    }
}
