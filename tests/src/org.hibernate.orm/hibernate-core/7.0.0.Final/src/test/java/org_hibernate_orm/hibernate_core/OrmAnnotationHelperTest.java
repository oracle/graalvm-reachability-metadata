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
import java.util.LinkedHashMap;
import java.util.Map;

import org.hibernate.boot.models.internal.OrmAnnotationHelper;
import org.hibernate.models.internal.BasicModelsContextImpl;
import org.hibernate.models.internal.SimpleClassLoading;
import org.hibernate.models.spi.AttributeDescriptor;
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
            try {

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

                Annotation usage = attributes.isEmpty() ? annotationDescriptor.createUsage(ctx)
                    : annotationDescriptor.createUsage(attributes, ctx);
                for (AttributeDescriptor<?> attributeDescriptor : annotationDescriptor.getAttributes()) {
                    OrmAnnotationHelper.extractJdkValue(usage, attributeDescriptor, ctx);
                }
            } catch (Exception e) {
                if (getRootCause(e) instanceof UnsupportedOperationException) {
                    // this is fine - trust me I'm an engineer!
                } else {
                    throw e;
                }
            }
        });
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
