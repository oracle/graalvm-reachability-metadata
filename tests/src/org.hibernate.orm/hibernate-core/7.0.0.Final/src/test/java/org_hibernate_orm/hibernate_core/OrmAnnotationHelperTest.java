/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.boot.models.internal.OrmAnnotationHelper;
import org.hibernate.models.internal.BasicModelsContextImpl;
import org.hibernate.models.internal.SimpleClassLoading;
import org.hibernate.models.spi.AnnotationDescriptor;
import org.hibernate.models.spi.AttributeDescriptor;
import org.hibernate.models.spi.ModelsContext;
import org.hibernate.models.spi.MutableAnnotationDescriptor;
import org.hibernate.models.spi.RegistryPrimer;
import org.hibernate.models.spi.ValueTypeDescriptor;
import org.junit.jupiter.api.Test;

/**
 * Test emulating Annotation inspection and potential instance creation done during Hibernate bootstrap.
 * Since the initialization routine is highly dependent on the context the tests use mocked and null values
 * to call methods, ignoring potential failures caused by argument validation.
 */
public class OrmAnnotationHelperTest {

    @Test
    public void captureWrappedAnnotations() {

        RegistryPrimer reg = (contributions, modelsContext) -> {
            // nothing to do here - just a test stub to satisfy the API contract
        };
        BasicModelsContextImpl ctx = new BasicModelsContextImpl(SimpleClassLoading.SIMPLE_CLASS_LOADING, true, reg);

        OrmAnnotationHelper.forEachOrmAnnotation(annotationDescriptor -> {

            Map<String, Object> attributes = createAttributeMap(annotationDescriptor);

            for (AttributeDescriptor<?> attributeDescriptor : annotationDescriptor.getAttributes()) {
                createInstances(annotationDescriptor, attributeDescriptor, attributes, ctx);
            }
        });
    }

    private static Map<String, Object> createAttributeMap(AnnotationDescriptor<?> annotationDescriptor) {

        Map<String, Object> attributes = new LinkedHashMap<>(annotationDescriptor.getAttributes().size());

        for (AttributeDescriptor<?> attributeDescriptor : annotationDescriptor.getAttributes()) {

            Class<?> valueType = attributeDescriptor.getTypeDescriptor().getValueType();

            if (valueType.isArray()) {

                ValueTypeDescriptor<?> typeDescriptor = attributeDescriptor.getTypeDescriptor();
                attributes.put(attributeDescriptor.getName(),
                    Array.newInstance(typeDescriptor.getValueType().getComponentType(), 0));
            } else {
                if (valueType == int.class || valueType == Integer.class) {
                    attributes.put(attributeDescriptor.getName(), 0);
                } else if (valueType == long.class || valueType == Long.class) {
                    attributes.put(attributeDescriptor.getName(), 0L);
                } else if (valueType == boolean.class || valueType == Boolean.class) {
                    attributes.put(attributeDescriptor.getName(), false);
                } else if (valueType == short.class || valueType == Short.class) {
                    attributes.put(attributeDescriptor.getName(), 0);
                } else if (valueType == byte.class || valueType == Byte.class) {
                    attributes.put(attributeDescriptor.getName(), 0);
                } else if (valueType == char.class || valueType == Character.class) {
                    attributes.put(attributeDescriptor.getName(), 'c');
                } else if (valueType == float.class || valueType == Float.class) {
                    attributes.put(attributeDescriptor.getName(), 0f);
                } else if (valueType == double.class || valueType == Double.class) {
                    attributes.put(attributeDescriptor.getName(), 0d);
                }
            }
        }
        return attributes;
    }

    private static void createInstances(AnnotationDescriptor<?> annotationDescriptor, AttributeDescriptor<?> attributeDescriptor, Map<String, Object> attributes, BasicModelsContextImpl ctx) {

        for (Annotation usage : usages(ctx, annotationDescriptor, attributes)) {
            instanceViaUsage(attributeDescriptor, ctx, usage);
        }

        instanceViaCtors(annotationDescriptor);
    }

    private static void instanceViaCtors(AnnotationDescriptor<?> annotationDescriptor) {
        if (annotationDescriptor instanceof MutableAnnotationDescriptor<?, ?> mad) {

            try {
                Constructor<?> constructor = mad.getMutableAnnotationType().getConstructor(annotationDescriptor.getAnnotationType(), ModelsContext.class);
                constructor.newInstance(null, null);
            } catch (Exception e) {
                // Ignore it - Hibernate does not expect null values, however we need to capture the constructor call.
            }
            try {
                Constructor<?> constructor = mad.getMutableAnnotationType().getConstructor(ModelsContext.class);
                constructor.newInstance(null);
            } catch (Exception e) {
                // Ignore it - Hibernate does not expect null values, however we need to capture the constructor call.
            }
        }
    }

    private static List<Annotation> usages(BasicModelsContextImpl ctx, AnnotationDescriptor<?> annotationDescriptor, Map<String, Object> attributes) {
        List<Annotation> usages = new ArrayList<>();
        try {
            usages.add(annotationDescriptor.createUsage(ctx));
        } catch (Exception e) {
            /*
             * Ignore it - Hibernate may not be happy with the context or attributes used in the test.
             * However we need to capture the reflective method invocations done by the OrmAnnotationDescriptor.
             */
        }
        try {
            usages.add(annotationDescriptor.createUsage(attributes, ctx));
        } catch (Exception e) {
            /*
             * Ignore it - Hibernate may not be happy with the context or attributes used in the test.
             * However we need to capture the reflective method invocations done by the OrmAnnotationDescriptor.
             */
        }
        return usages;
    }

    private static void instanceViaUsage(AttributeDescriptor<?> attributeDescriptor, BasicModelsContextImpl ctx, Annotation usage) {
        try {
            OrmAnnotationHelper.extractJdkValue(usage, attributeDescriptor, ctx);
        } catch (Exception e) {
            Throwable rootCause = getRootCause(e);
            if (rootCause instanceof UnsupportedOperationException || rootCause instanceof NullPointerException) {
                /*
                 * Ignore it - Hibernate may not be happy with the context or attributes used in the test.
                 * However we need to capture the reflective method invocations done by the OrmAnnotationHelper.
                 */
            } else {
                throw e;
            }
        }
    }

    private static Throwable getRootCause(Throwable e) {

        Throwable cause = null;
        Throwable result = e;

        while (null != (cause = result.getCause()) && (result != cause)) {
            result = cause;
        }
        return result;
    }
}
