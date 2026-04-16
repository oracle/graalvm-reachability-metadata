/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_validation.jakarta_validation_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import jakarta.validation.BootstrapConfiguration;
import jakarta.validation.ClockProvider;
import jakarta.validation.Configuration;
import jakarta.validation.ConstraintValidatorFactory;
import jakarta.validation.MessageInterpolator;
import jakarta.validation.ParameterNameProvider;
import jakarta.validation.TraversableResolver;
import jakarta.validation.Validation;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorContext;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.executable.ExecutableType;
import jakarta.validation.spi.BootstrapState;
import jakarta.validation.spi.ConfigurationState;
import jakarta.validation.spi.ValidationProvider;
import jakarta.validation.valueextraction.ValueExtractor;
import org.junit.jupiter.api.Test;

public class ValidationNewProviderInstanceTest {

    @Test
    void byProviderConfigureInstantiatesTheRequestedProvider() {
        TestValidationProvider.reset();

        TestValidationConfiguration configuration = Validation.byProvider(TestValidationProvider.class).configure();

        assertThat(TestValidationProvider.instantiationCount).isEqualTo(1);
        assertThat(TestValidationProvider.lastInstance).isNotNull();
        assertThat(configuration.provider()).isSameAs(TestValidationProvider.lastInstance);
        assertThat(configuration.bootstrapState()).isNotNull();
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
        public TestValidationConfiguration constraintValidatorFactory(ConstraintValidatorFactory constraintValidatorFactory) {
            return this;
        }

        @Override
        public TestValidationConfiguration parameterNameProvider(ParameterNameProvider parameterNameProvider) {
            return this;
        }

        @Override
        public TestValidationConfiguration clockProvider(ClockProvider clockProvider) {
            return this;
        }

        @Override
        public TestValidationConfiguration addValueExtractor(ValueExtractor<?> extractor) {
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
        public ParameterNameProvider getDefaultParameterNameProvider() {
            return null;
        }

        @Override
        public ClockProvider getDefaultClockProvider() {
            return null;
        }

        @Override
        public BootstrapConfiguration getBootstrapConfiguration() {
            return new TestBootstrapConfiguration();
        }

        @Override
        public ValidatorFactory buildValidatorFactory() {
            return new TestValidatorFactory();
        }
    }

    private static final class TestBootstrapConfiguration implements BootstrapConfiguration {

        @Override
        public String getDefaultProviderClassName() {
            return null;
        }

        @Override
        public String getConstraintValidatorFactoryClassName() {
            return null;
        }

        @Override
        public String getMessageInterpolatorClassName() {
            return null;
        }

        @Override
        public String getTraversableResolverClassName() {
            return null;
        }

        @Override
        public String getParameterNameProviderClassName() {
            return null;
        }

        @Override
        public String getClockProviderClassName() {
            return null;
        }

        @Override
        public Set<String> getValueExtractorClassNames() {
            return Set.of();
        }

        @Override
        public Set<String> getConstraintMappingResourcePaths() {
            return Set.of();
        }

        @Override
        public boolean isExecutableValidationEnabled() {
            return true;
        }

        @Override
        public Set<ExecutableType> getDefaultValidatedExecutableTypes() {
            return EnumSet.of(ExecutableType.CONSTRUCTORS, ExecutableType.NON_GETTER_METHODS);
        }

        @Override
        public Map<String, String> getProperties() {
            return Map.of();
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
        public ParameterNameProvider getParameterNameProvider() {
            return null;
        }

        @Override
        public ClockProvider getClockProvider() {
            return null;
        }

        @Override
        public <T> T unwrap(Class<T> type) {
            throw new ValidationException("No provider-specific API available for " + type.getName());
        }

        @Override
        public void close() {
        }
    }
}
