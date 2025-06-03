/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
 * @author Christoph Strobl
 */
public class OrmAnnotationHelperTest {

    @Test
    public void captureWrappedAnnotations() {

        RegistryPrimer reg = (contributions, modelsContext) -> {
            // forget this one.
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
                // ignore it
            }
            try {
                Constructor<?> constructor = mad.getMutableAnnotationType().getConstructor(ModelsContext.class);
                constructor.newInstance(null);
            } catch (Exception e) {
                // ignore it
            }
        }
    }

    private static List<Annotation> usages(BasicModelsContextImpl ctx, AnnotationDescriptor<?> annotationDescriptor, Map<String, Object> attributes) {
        List<Annotation> usages = new ArrayList<>();
        try {
            usages.add(annotationDescriptor.createUsage(ctx));
        } catch (Exception e) {
        }
        try {
            usages.add(annotationDescriptor.createUsage(attributes, ctx));
        } catch (Exception e) {
        }
        return usages;
    }

    private static void instanceViaUsage(AttributeDescriptor<?> attributeDescriptor, BasicModelsContextImpl ctx, Annotation usage) {
        try {
            OrmAnnotationHelper.extractJdkValue(usage, attributeDescriptor, ctx);
        } catch (Exception e) {
            Throwable rootCause = getRootCause(e);
            if (rootCause instanceof UnsupportedOperationException || rootCause instanceof NullPointerException) {
                // this is fine - trust me I'm an engineer!
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
