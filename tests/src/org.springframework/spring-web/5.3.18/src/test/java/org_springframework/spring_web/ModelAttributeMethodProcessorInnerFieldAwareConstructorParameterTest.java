/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.beans.ConstructorProperties;
import java.lang.reflect.Method;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import org.springframework.core.MethodParameter;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.support.ConfigurableWebBindingInitializer;
import org.springframework.web.bind.support.DefaultDataBinderFactory;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.annotation.ModelAttributeMethodProcessor;
import org.springframework.web.method.support.ModelAndViewContainer;

import static org.assertj.core.api.Assertions.assertThat;

public class ModelAttributeMethodProcessorInnerFieldAwareConstructorParameterTest {
    @Test
    void usesFieldAnnotationsWhenConvertingConstructorArguments() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addParameter("eventDate", "10/05/2026");
        NativeWebRequest webRequest = new ServletWebRequest(request);
        ModelAttributeMethodProcessor processor = new ModelAttributeMethodProcessor(true);
        MethodParameter parameter = handlerMethodParameter();
        WebDataBinderFactory binderFactory = formattingBinderFactory();

        EventForm actual = (EventForm) processor.resolveArgument(
                parameter, new ModelAndViewContainer(), webRequest, binderFactory);

        assertThat(actual.getEventDate()).isEqualTo(LocalDate.of(2026, 5, 10));
    }

    private static MethodParameter handlerMethodParameter() throws NoSuchMethodException {
        Method method = ModelAttributeMethodProcessorInnerFieldAwareConstructorParameterTest.class
                .getDeclaredMethod("handle", EventForm.class);
        return new MethodParameter(method, 0);
    }

    private static WebDataBinderFactory formattingBinderFactory() {
        ConfigurableWebBindingInitializer initializer = new ConfigurableWebBindingInitializer();
        initializer.setConversionService(new DefaultFormattingConversionService());
        return new DefaultDataBinderFactory(initializer);
    }

    @SuppressWarnings("unused")
    private void handle(@ModelAttribute(value = "eventForm", binding = false) EventForm eventForm) {
    }

    public static final class EventForm {
        @DateTimeFormat(pattern = "dd/MM/yyyy")
        private final LocalDate eventDate;

        @ConstructorProperties("eventDate")
        public EventForm(LocalDate eventDate) {
            this.eventDate = eventDate;
        }

        public LocalDate getEventDate() {
            return this.eventDate;
        }
    }
}
