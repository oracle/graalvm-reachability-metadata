/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_models.hibernate_models;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.Map;

import org.hibernate.models.internal.AnnotationUsageHelper;
import org.hibernate.models.internal.BasicModelsContextImpl;
import org.hibernate.models.internal.SimpleClassLoading;
import org.hibernate.models.spi.AnnotationDescriptor;
import org.hibernate.models.spi.ModelsContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotationUsageHelperTest {
    @Test
    public void combinesSingularAndContainerUsages() {
        final ModelsContext context = newModelsContext();
        final AnnotationDescriptor<RepeatableLabel> descriptor = descriptor(context);
        final Map<Class<? extends Annotation>, Annotation> usageMap = usageMap(
                new RepeatableLabelUsage("direct"),
                new RepeatableLabelsUsage(
                        new RepeatableLabelUsage("container-first"),
                        new RepeatableLabelUsage("container-second")
                )
        );

        final RepeatableLabel[] usages = AnnotationUsageHelper.getRepeatedUsages(descriptor, usageMap, context);

        assertValues(usages, "direct", "container-first", "container-second");
    }

    @Test
    public void createsSingletonArrayForSingularUsage() {
        final ModelsContext context = newModelsContext();
        final AnnotationDescriptor<RepeatableLabel> descriptor = descriptor(context);
        final Map<Class<? extends Annotation>, Annotation> usageMap = usageMap(
                new RepeatableLabelUsage("only-direct"),
                null
        );

        final RepeatableLabel[] usages = AnnotationUsageHelper.getRepeatedUsages(descriptor, usageMap, context);

        assertValues(usages, "only-direct");
    }

    @Test
    public void createsEmptyArrayWhenNoUsageExists() {
        final ModelsContext context = newModelsContext();
        final AnnotationDescriptor<RepeatableLabel> descriptor = descriptor(context);

        final RepeatableLabel[] usages = AnnotationUsageHelper.getRepeatedUsages(
                descriptor,
                Map.<Class<? extends Annotation>, Annotation>of(),
                context
        );

        assertEquals(0, usages.length);
    }

    private static ModelsContext newModelsContext() {
        return new BasicModelsContextImpl(SimpleClassLoading.SIMPLE_CLASS_LOADING, false, null);
    }

    private static AnnotationDescriptor<RepeatableLabel> descriptor(ModelsContext context) {
        return context.getAnnotationDescriptorRegistry().getDescriptor(RepeatableLabel.class);
    }

    private static Map<Class<? extends Annotation>, Annotation> usageMap(
            RepeatableLabel usage,
            RepeatableLabels containerUsage) {
        final Map<Class<? extends Annotation>, Annotation> usageMap = new HashMap<>();
        if (usage != null) {
            usageMap.put(RepeatableLabel.class, usage);
        }
        if (containerUsage != null) {
            usageMap.put(RepeatableLabels.class, containerUsage);
        }
        return usageMap;
    }

    private static void assertValues(RepeatableLabel[] usages, String... expectedValues) {
        assertEquals(expectedValues.length, usages.length);
        for (int i = 0; i < expectedValues.length; i++) {
            assertEquals(expectedValues[i], usages[i].value());
        }
    }

    @Repeatable(RepeatableLabels.class)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface RepeatableLabel {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    public @interface RepeatableLabels {
        RepeatableLabel[] value();
    }

    private static final class RepeatableLabelUsage implements RepeatableLabel {
        private final String value;

        private RepeatableLabelUsage(String value) {
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return RepeatableLabel.class;
        }
    }

    private static final class RepeatableLabelsUsage implements RepeatableLabels {
        private final RepeatableLabel[] value;

        private RepeatableLabelsUsage(RepeatableLabel... value) {
            this.value = value;
        }

        @Override
        public RepeatableLabel[] value() {
            return value;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return RepeatableLabels.class;
        }
    }
}
