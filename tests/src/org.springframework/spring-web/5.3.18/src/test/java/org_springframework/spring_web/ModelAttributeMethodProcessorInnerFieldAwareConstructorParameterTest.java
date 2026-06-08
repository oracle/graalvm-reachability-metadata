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
import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.support.DefaultDataBinderFactory;
import org.springframework.web.bind.support.WebRequestDataBinder;
import org.springframework.web.context.request.NativeWebRequest;
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
        NativeWebRequest webRequest = new SimpleNativeWebRequest("name", "spring-web");

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

    private static final class SimpleNativeWebRequest implements NativeWebRequest {

        private final Map<String, String[]> parameters;

        private final Map<String, Object> requestAttributes = new HashMap<>();

        private final Map<String, Object> sessionAttributes = new HashMap<>();

        private SimpleNativeWebRequest(String parameterName, String parameterValue) {
            this.parameters = Collections.singletonMap(parameterName, new String[] { parameterValue });
        }

        @Override
        public Object getNativeRequest() {
            return this;
        }

        @Override
        public Object getNativeResponse() {
            return null;
        }

        @Override
        public <T> T getNativeRequest(Class<T> requiredType) {
            return requiredType != null && requiredType.isInstance(this) ? requiredType.cast(this) : null;
        }

        @Override
        public <T> T getNativeResponse(Class<T> requiredType) {
            return null;
        }

        @Override
        public String getHeader(String headerName) {
            return null;
        }

        @Override
        public String[] getHeaderValues(String headerName) {
            return null;
        }

        @Override
        public Iterator<String> getHeaderNames() {
            return Collections.emptyIterator();
        }

        @Override
        public String getParameter(String paramName) {
            String[] values = this.parameters.get(paramName);
            return values != null && values.length > 0 ? values[0] : null;
        }

        @Override
        public String[] getParameterValues(String paramName) {
            return this.parameters.get(paramName);
        }

        @Override
        public Iterator<String> getParameterNames() {
            return this.parameters.keySet().iterator();
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return Collections.unmodifiableMap(this.parameters);
        }

        @Override
        public Locale getLocale() {
            return Locale.ENGLISH;
        }

        @Override
        public String getContextPath() {
            return "";
        }

        @Override
        public String getRemoteUser() {
            return null;
        }

        @Override
        public Principal getUserPrincipal() {
            return null;
        }

        @Override
        public boolean isUserInRole(String role) {
            return false;
        }

        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public boolean checkNotModified(long lastModifiedTimestamp) {
            return false;
        }

        @Override
        public boolean checkNotModified(String etag) {
            return false;
        }

        @Override
        public boolean checkNotModified(String etag, long lastModifiedTimestamp) {
            return false;
        }

        @Override
        public String getDescription(boolean includeClientInfo) {
            return "simple native web request";
        }

        @Override
        public Object getAttribute(String name, int scope) {
            return attributes(scope).get(name);
        }

        @Override
        public void setAttribute(String name, Object value, int scope) {
            attributes(scope).put(name, value);
        }

        @Override
        public void removeAttribute(String name, int scope) {
            attributes(scope).remove(name);
        }

        @Override
        public String[] getAttributeNames(int scope) {
            return attributes(scope).keySet().toArray(new String[0]);
        }

        @Override
        public void registerDestructionCallback(String name, Runnable callback, int scope) {
        }

        @Override
        public Object resolveReference(String key) {
            return REFERENCE_REQUEST.equals(key) ? this : null;
        }

        @Override
        public String getSessionId() {
            return "simple-session";
        }

        @Override
        public Object getSessionMutex() {
            return this.sessionAttributes;
        }

        private Map<String, Object> attributes(int scope) {
            return scope == SCOPE_SESSION ? this.sessionAttributes : this.requestAttributes;
        }
    }
}
