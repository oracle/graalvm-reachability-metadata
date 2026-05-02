/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.dev.javac.testing.GeneratorContextBuilder;
import com.google.gwt.dev.javac.testing.Source;
import com.google.gwt.validation.rebind.BeanHelper;
import com.google.gwt.validation.rebind.BeanHelperCache;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.annotation.ElementType;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.validation.Configuration;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorFactory;
import javax.validation.ConstraintViolation;
import javax.validation.MessageInterpolator;
import javax.validation.TraversableResolver;
import javax.validation.ValidationException;
import javax.validation.Validator;
import javax.validation.ValidatorContext;
import javax.validation.ValidatorFactory;
import javax.validation.metadata.BeanDescriptor;
import javax.validation.metadata.ConstraintDescriptor;
import javax.validation.metadata.ElementDescriptor;
import javax.validation.metadata.PropertyDescriptor;
import javax.validation.metadata.Scope;
import javax.validation.spi.BootstrapState;
import javax.validation.spi.ConfigurationState;
import javax.validation.spi.ValidationProvider;

public class BeanHelperCacheTest {
    private static final String PACKAGE_NAME = "org_gwtproject.gwt_user";

    @Test
    void createsHelperForCascadedIterableElementType() throws Exception {
        try {
            GeneratorContext context = createGeneratorContext();
            BeanHelperCache cache = new BeanHelperCache();
            BeanHelper helper = cache.createHelper(BeanHelperCacheRootBean.class, TreeLogger.NULL,
                    context);

            assertThat(helper.getClazz()).isEqualTo(BeanHelperCacheRootBean.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static GeneratorContext createGeneratorContext() {
        GeneratorContextBuilder builder = GeneratorContextBuilder.newCoreBasedBuilder();
        builder.add(source("BeanHelperCacheRootBean", """
                package org_gwtproject.gwt_user;

                import java.util.List;

                class BeanHelperCacheRootBean {
                  List<BeanHelperCacheChildBean> children;
                }
                """));
        builder.add(source("BeanHelperCacheChildBean", """
                package org_gwtproject.gwt_user;

                class BeanHelperCacheChildBean {
                }
                """));
        return builder.buildGeneratorContext();
    }

    private static Source source(String typeName, String source) {
        return new Source() {
            @Override
            public String getPath() {
                return PACKAGE_NAME.replace('.', '/') + "/" + typeName + ".java";
            }

            @Override
            public String getSource() {
                return source;
            }
        };
    }

    public static final class TestValidationProvider
            implements ValidationProvider<TestValidationConfiguration> {
        @Override
        public TestValidationConfiguration createSpecializedConfiguration(BootstrapState state) {
            return new TestValidationConfiguration(this);
        }

        @Override
        public Configuration<?> createGenericConfiguration(BootstrapState state) {
            return new TestValidationConfiguration(this);
        }

        @Override
        public ValidatorFactory buildValidatorFactory(ConfigurationState configurationState) {
            return new TestValidatorFactory();
        }
    }

    public static final class TestValidationConfiguration
            implements Configuration<TestValidationConfiguration> {
        private final TestValidationProvider provider;

        private TestValidationConfiguration(TestValidationProvider provider) {
            this.provider = provider;
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
                ConstraintValidatorFactory constraintValidatorFactory) {
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
            return TestMessageInterpolator.INSTANCE;
        }

        @Override
        public TraversableResolver getDefaultTraversableResolver() {
            return TestTraversableResolver.INSTANCE;
        }

        @Override
        public ConstraintValidatorFactory getDefaultConstraintValidatorFactory() {
            return new TestConstraintValidatorFactory();
        }

        @Override
        public ValidatorFactory buildValidatorFactory() {
            return provider.buildValidatorFactory(new TestConfigurationState(this));
        }
    }

    private static final class TestConfigurationState implements ConfigurationState {
        private final TestValidationConfiguration configuration;

        private TestConfigurationState(TestValidationConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        public boolean isIgnoreXmlConfiguration() {
            return true;
        }

        @Override
        public MessageInterpolator getMessageInterpolator() {
            return configuration.getDefaultMessageInterpolator();
        }

        @Override
        public Set<InputStream> getMappingStreams() {
            return Collections.emptySet();
        }

        @Override
        public ConstraintValidatorFactory getConstraintValidatorFactory() {
            return configuration.getDefaultConstraintValidatorFactory();
        }

        @Override
        public TraversableResolver getTraversableResolver() {
            return configuration.getDefaultTraversableResolver();
        }

        @Override
        public Map<String, String> getProperties() {
            return Collections.emptyMap();
        }
    }

    private static final class TestValidatorFactory implements ValidatorFactory {
        private final Validator validator = new TestValidator();

        @Override
        public Validator getValidator() {
            return validator;
        }

        @Override
        public ValidatorContext usingContext() {
            return new TestValidatorContext(validator);
        }

        @Override
        public MessageInterpolator getMessageInterpolator() {
            return TestMessageInterpolator.INSTANCE;
        }

        @Override
        public TraversableResolver getTraversableResolver() {
            return TestTraversableResolver.INSTANCE;
        }

        @Override
        public ConstraintValidatorFactory getConstraintValidatorFactory() {
            return new TestConstraintValidatorFactory();
        }

        @Override
        public <T> T unwrap(Class<T> type) {
            String message = "No provider-specific API available for " + type.getName();
            throw new ValidationException(message);
        }
    }

    private enum TestMessageInterpolator implements MessageInterpolator {
        INSTANCE;

        @Override
        public String interpolate(String messageTemplate, MessageInterpolator.Context context) {
            return messageTemplate;
        }

        @Override
        public String interpolate(String messageTemplate, MessageInterpolator.Context context,
                java.util.Locale locale) {
            return messageTemplate;
        }
    }

    private enum TestTraversableResolver implements TraversableResolver {
        INSTANCE;

        @Override
        public boolean isReachable(Object traversableObject,
                javax.validation.Path.Node traversableProperty, Class<?> rootBeanType,
                javax.validation.Path pathToTraversableObject, ElementType elementType) {
            return true;
        }

        @Override
        public boolean isCascadable(Object traversableObject,
                javax.validation.Path.Node traversableProperty, Class<?> rootBeanType,
                javax.validation.Path pathToTraversableObject, ElementType elementType) {
            return true;
        }
    }

    private static final class TestValidatorContext implements ValidatorContext {
        private final Validator validator;

        private TestValidatorContext(Validator validator) {
            this.validator = validator;
        }

        @Override
        public ValidatorContext messageInterpolator(MessageInterpolator messageInterpolator) {
            return this;
        }

        @Override
        public ValidatorContext traversableResolver(TraversableResolver traversableResolver) {
            return this;
        }

        @Override
        public ValidatorContext constraintValidatorFactory(
                ConstraintValidatorFactory constraintValidatorFactory) {
            return this;
        }

        @Override
        public Validator getValidator() {
            return validator;
        }
    }

    private static final class TestConstraintValidatorFactory
            implements ConstraintValidatorFactory {
        @Override
        public <T extends ConstraintValidator<?, ?>> T getInstance(Class<T> key) {
            throw new ValidationException("Constraint validators are not used by this test");
        }
    }

    private static final class TestValidator implements Validator {
        @Override
        public <T> Set<ConstraintViolation<T>> validate(T object, Class<?>... groups) {
            return Collections.emptySet();
        }

        @Override
        public <T> Set<ConstraintViolation<T>> validateProperty(T object, String propertyName,
                Class<?>... groups) {
            return Collections.emptySet();
        }

        @Override
        public <T> Set<ConstraintViolation<T>> validateValue(Class<T> beanType, String propertyName,
                Object value, Class<?>... groups) {
            return Collections.emptySet();
        }

        @Override
        public BeanDescriptor getConstraintsForClass(Class<?> clazz) {
            if (BeanHelperCacheRootBean.class.equals(clazz)) {
                PropertyDescriptor children = new TestPropertyDescriptor(
                        "children", List.class, true);
                return new TestBeanDescriptor(clazz, true, Set.of(children));
            }
            return new TestBeanDescriptor(clazz, true, Collections.emptySet());
        }

        @Override
        public <T> T unwrap(Class<T> type) {
            String message = "No provider-specific API available for " + type.getName();
            throw new ValidationException(message);
        }
    }

    private static class TestElementDescriptor implements ElementDescriptor {
        private final Class<?> elementClass;
        private final boolean constrained;

        TestElementDescriptor(Class<?> elementClass, boolean constrained) {
            this.elementClass = elementClass;
            this.constrained = constrained;
        }

        @Override
        public boolean hasConstraints() {
            return constrained;
        }

        @Override
        public Class<?> getElementClass() {
            return elementClass;
        }

        @Override
        public Set<ConstraintDescriptor<?>> getConstraintDescriptors() {
            return Collections.emptySet();
        }

        @Override
        public ConstraintFinder findConstraints() {
            return EmptyConstraintFinder.INSTANCE;
        }
    }

    private static final class TestBeanDescriptor extends TestElementDescriptor
            implements BeanDescriptor {
        private final Set<PropertyDescriptor> constrainedProperties;

        private TestBeanDescriptor(Class<?> elementClass, boolean constrained,
                Set<PropertyDescriptor> constrainedProperties) {
            super(elementClass, constrained);
            this.constrainedProperties = constrainedProperties;
        }

        @Override
        public boolean isBeanConstrained() {
            return hasConstraints() || !constrainedProperties.isEmpty();
        }

        @Override
        public PropertyDescriptor getConstraintsForProperty(String propertyName) {
            return constrainedProperties.stream()
                    .filter(property -> propertyName.equals(property.getPropertyName()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public Set<PropertyDescriptor> getConstrainedProperties() {
            return constrainedProperties;
        }
    }

    private static final class TestPropertyDescriptor extends TestElementDescriptor
            implements PropertyDescriptor {
        private final String propertyName;
        private final boolean cascaded;

        private TestPropertyDescriptor(String propertyName, Class<?> elementClass,
                boolean cascaded) {
            super(elementClass, cascaded);
            this.propertyName = propertyName;
            this.cascaded = cascaded;
        }

        @Override
        public boolean isCascaded() {
            return cascaded;
        }

        @Override
        public String getPropertyName() {
            return propertyName;
        }
    }

    private enum EmptyConstraintFinder implements ElementDescriptor.ConstraintFinder {
        INSTANCE;

        @Override
        public ElementDescriptor.ConstraintFinder unorderedAndMatchingGroups(Class<?>... groups) {
            return this;
        }

        @Override
        public ElementDescriptor.ConstraintFinder lookingAt(Scope scope) {
            return this;
        }

        @Override
        public ElementDescriptor.ConstraintFinder declaredOn(ElementType... types) {
            return this;
        }

        @Override
        public Set<ConstraintDescriptor<?>> getConstraintDescriptors() {
            return Collections.emptySet();
        }

        @Override
        public boolean hasConstraints() {
            return false;
        }
    }
}

class BeanHelperCacheRootBean {
    List<BeanHelperCacheChildBean> children;
}

class BeanHelperCacheChildBean {
}
