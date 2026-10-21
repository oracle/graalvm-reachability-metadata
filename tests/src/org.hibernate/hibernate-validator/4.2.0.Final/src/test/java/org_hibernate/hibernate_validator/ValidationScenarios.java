/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_validator;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.Constraint;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.ConstraintViolation;
import javax.validation.GroupSequence;
import javax.validation.MessageInterpolator;
import javax.validation.OverridesAttribute;
import javax.validation.Payload;
import javax.validation.ReportAsSingleViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidationProviderResolver;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import javax.validation.spi.ValidationProvider;
import org.hibernate.validator.HibernateValidator;
import org.hibernate.validator.HibernateValidatorConfiguration;
import org.hibernate.validator.cfg.ConstraintMapping;
import org.hibernate.validator.cfg.defs.NotNullDef;
import org.hibernate.validator.cfg.defs.SizeDef;
import org.hibernate.validator.group.DefaultGroupSequenceProvider;
import org.hibernate.validator.group.GroupSequenceProvider;
import org.hibernate.validator.messageinterpolation.ResourceBundleMessageInterpolator;
import org.hibernate.validator.method.MethodValidator;
import org.hibernate.validator.method.metadata.MethodDescriptor;

public final class ValidationScenarios {
    private ValidationScenarios() {}

    public static void assertFieldValidation() {
        Set<ConstraintViolation<FieldBean>> violations = validator().validate(new FieldBean());

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("name");
    }

    public static void assertGetterValidation() {
        Set<ConstraintViolation<GetterBean>> violations = validator().validate(new GetterBean("x"));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getInvalidValue()).isEqualTo("x");
    }

    public static void assertRepeatedConstraints() {
        Set<ConstraintViolation<RepeatedPatternBean>> violations =
                validator().validate(new RepeatedPatternBean("12"));

        assertThat(violations).hasSize(2);
    }

    public static void assertComposedConstraint() {
        Set<ConstraintViolation<ComposedBean>> violations =
                validator().validate(new ComposedBean("ab"));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getConstraintDescriptor().getAttributes())
                .containsEntry("minimum", 4);
    }

    public static void assertProgrammaticConstraint() {
        ConstraintMapping mapping = new ConstraintMapping();
        mapping.type(ProgrammaticBean.class)
                .property("name", ElementType.FIELD)
                .constraint(new SizeDef().min(3).max(8));
        Validator validator = configuration().addMapping(mapping).buildValidatorFactory().getValidator();

        Set<ConstraintViolation<ProgrammaticBean>> violations =
                validator.validate(new ProgrammaticBean("x"));
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getConstraintDescriptor().getAnnotation().annotationType())
                .isEqualTo(Size.class);
    }

    public static void assertProgrammaticMethodMetadata() {
        ConstraintMapping mapping = new ConstraintMapping();
        mapping.type(MethodBean.class)
                .method("greet", String.class)
                .parameter(0)
                .constraint(new NotNullDef());
        Validator validator = configuration().addMapping(mapping).buildValidatorFactory().getValidator();
        MethodValidator methodValidator = validator.unwrap(MethodValidator.class);

        MethodDescriptor descriptor =
                methodValidator
                        .getConstraintsForType(MethodBean.class)
                        .getConstraintsForMethod("greet", String.class);
        assertThat(descriptor).isNotNull();
        assertThat(descriptor.getMethodName()).isEqualTo("greet");
        assertThat(descriptor.getParameterDescriptors().get(0).hasConstraints()).isTrue();
    }

    public static void assertArrayValidator() {
        Set<ConstraintViolation<ArrayBean>> violations =
                validator().validate(new ArrayBean(new String[] {"valid", null}));

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getInvalidValue()).isInstanceOf(String[].class);
    }

    public static void assertGroupSequence() {
        Set<ConstraintViolation<GroupBean>> violations = validator().validate(new GroupBean());

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("first");
    }

    public static void assertDynamicGroupSequence() {
        Set<ConstraintViolation<DynamicGroupBean>> violations =
                validator().validate(new DynamicGroupBean());

        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("first");
    }

    public static void assertXmlConfiguration() {
        Thread thread = Thread.currentThread();
        ClassLoader originalContextClassLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(new EmptyContextClassLoader());
        try {
            Set<ConstraintViolation<XmlMappedBean>> violations =
                    Validation.byProvider(HibernateValidator.class)
                            .providerResolver(new HibernateProviderResolver())
                            .configure()
                            .buildValidatorFactory()
                            .getValidator()
                            .validate(new XmlMappedBean("123"));

            assertThat(violations).hasSize(1);
            assertThat(violations.iterator().next().getMessage()).startsWith("xml:");
        } finally {
            thread.setContextClassLoader(originalContextClassLoader);
        }
    }

    public static void assertResourceBundleInterpolation() {
        MessageInterpolator interpolator = new ResourceBundleMessageInterpolator();
        Validator validator =
                configuration()
                        .messageInterpolator(interpolator)
                        .buildValidatorFactory()
                        .getValidator();

        Set<ConstraintViolation<FieldBean>> violations = validator.validate(new FieldBean());
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getMessage()).doesNotStartWith("{");
    }

    private static Validator validator() {
        return configuration().buildValidatorFactory().getValidator();
    }

    private static HibernateValidatorConfiguration configuration() {
        return Validation.byProvider(HibernateValidator.class).configure().ignoreXmlConfiguration();
    }

    public static class FieldBean {
        @NotNull private String name;
    }

    public static class GetterBean {
        private final String code;

        public GetterBean(String code) {
            this.code = code;
        }

        @Size(min = 3)
        public String getCode() {
            return code;
        }
    }

    public static class RepeatedPatternBean {
        @Pattern.List({@Pattern(regexp = "[A-Z]+"), @Pattern(regexp = ".{3,}")})
        private final String code;

        public RepeatedPatternBean(String code) {
            this.code = code;
        }
    }

    public static class ComposedBean {
        @StrongName(minimum = 4)
        private final String name;

        public ComposedBean(String name) {
            this.name = name;
        }
    }

    public static class ProgrammaticBean {
        private final String name;

        public ProgrammaticBean(String name) {
            this.name = name;
        }
    }

    public static class MethodBean {
        public String greet(String name) {
            return "Hello " + name;
        }
    }

    public static class ArrayBean {
        @NonNullElements private final String[] values;

        public ArrayBean(String[] values) {
            this.values = values;
        }
    }

    public interface First {}

    public interface Second {}

    @GroupSequence({GroupBean.class, First.class, Second.class})
    public static class GroupBean {
        @NotNull(groups = First.class)
        private String first;

        @NotNull(groups = Second.class)
        private String second;
    }

    @GroupSequenceProvider(DynamicGroupSequenceProvider.class)
    public static class DynamicGroupBean {
        @NotNull(groups = First.class)
        private String first;

        @NotNull(groups = Second.class)
        private String second;
    }

    public static class DynamicGroupSequenceProvider
            implements DefaultGroupSequenceProvider<DynamicGroupBean> {
        @Override
        public List<Class<?>> getValidationGroups(DynamicGroupBean bean) {
            return Arrays.<Class<?>>asList(DynamicGroupBean.class, First.class, Second.class);
        }
    }

    private static class EmptyContextClassLoader extends ClassLoader {
        EmptyContextClassLoader() {
            super(null);
        }
    }

    private static class HibernateProviderResolver implements ValidationProviderResolver {
        @Override
        public List<ValidationProvider<?>> getValidationProviders() {
            return Collections.<ValidationProvider<?>>singletonList(new HibernateValidator());
        }
    }

    @NotNull
    @Size(min = 2)
    @Constraint(validatedBy = {})
    @ReportAsSingleViolation
    @Target({ElementType.FIELD, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface StrongName {
        String message() default "strong name required";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};

        @OverridesAttribute(constraint = Size.class, name = "min")
        int minimum() default 3;
    }

    @Constraint(validatedBy = StringArrayValidator.class)
    @Target({ElementType.FIELD, ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface NonNullElements {
        String message() default "array elements must not be null";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    public static class StringArrayValidator
            implements ConstraintValidator<NonNullElements, String[]> {
        @Override
        public void initialize(NonNullElements constraintAnnotation) {}

        @Override
        public boolean isValid(String[] values, ConstraintValidatorContext context) {
            if (values == null) {
                return true;
            }
            for (String value : values) {
                if (value == null) {
                    return false;
                }
            }
            return true;
        }
    }
}
