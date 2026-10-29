/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.beans.ConstructorProperties;
import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Test;

import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.support.DefaultDataBinderFactory;
import org.springframework.web.bind.support.WebRequestDataBinder;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.ModelAttributeMethodProcessor;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.assertj.core.api.Assertions.assertThat;

public class ModelAttributeMethodProcessorInnerFieldAwareConstructorParameterTest {

    @Test
    void resolveArgumentExposesConstructorFieldAnnotationsDuringBinding() throws Exception {
        HandlerMethod handlerMethod = new HandlerMethod(
                new AttributeController(), "handle", ConstructorBackedForm.class);
        MethodParameter parameter = handlerMethod.getMethodParameters()[0];
        AnnotationCapturingBinderFactory binderFactory = new AnnotationCapturingBinderFactory();
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addParameter("name", "spring-web");
        NativeWebRequest webRequest = new ServletWebRequest(servletRequest);

        Object attribute = new ModelAttributeMethodProcessor(false).resolveArgument(
                parameter, new ModelAndViewContainer(), webRequest, binderFactory);

        assertThat(attribute).isInstanceOf(ConstructorBackedForm.class);
        assertThat(((ConstructorBackedForm) attribute).getName()).isEqualTo("spring-web");
        assertThat(binderFactory.sawConstructorFieldAnnotation()).isTrue();
    }

    private static final class AttributeController {

        @SuppressWarnings("unused")
        public void handle(@ModelAttribute ConstructorBackedForm form) {
        }
    }

    private static final class ConstructorBackedForm {

        @ConstructorField
        private final String name;

        @ConstructorProperties("name")
        ConstructorBackedForm(String name) {
            this.name = name;
        }

        public String getName() {
            return this.name;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    private @interface ConstructorField {
    }

    private static final class AnnotationCapturingBinderFactory extends DefaultDataBinderFactory {

        private boolean constructorFieldAnnotationSeen;

        private AnnotationCapturingBinderFactory() {
            super(null);
        }

        @Override
        protected WebDataBinder createBinderInstance(Object target, String objectName, NativeWebRequest webRequest) {
            return new AnnotationCapturingBinder(target, objectName, this);
        }

        private void recordConstructorFieldAnnotation() {
            this.constructorFieldAnnotationSeen = true;
        }

        private boolean sawConstructorFieldAnnotation() {
            return this.constructorFieldAnnotationSeen;
        }
    }

    private static final class AnnotationCapturingBinder extends WebRequestDataBinder {

        private final AnnotationCapturingBinderFactory binderFactory;

        private AnnotationCapturingBinder(Object target, String objectName,
                AnnotationCapturingBinderFactory binderFactory) {

            super(target, objectName);
            this.binderFactory = binderFactory;
        }

        @Override
        public <T> T convertIfNecessary(Object value, Class<T> requiredType, MethodParameter methodParam)
                throws TypeMismatchException {

            if (methodParam != null) {
                for (Annotation annotation : methodParam.getParameterAnnotations()) {
                    if (annotation instanceof ConstructorField) {
                        this.binderFactory.recordConstructorFieldAnnotation();
                    }
                }
            }
            return super.convertIfNecessary(value, requiredType, methodParam);
        }
    }
}
