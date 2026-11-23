/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_web;

import java.lang.annotation.Annotation;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.Mapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;

public class HandlerMethodTest {

    @Test
    void methodParametersIncludeInterfaceParameterAnnotations() throws Exception {
        HandlerMethod handlerMethod = new HandlerMethod(
                new AccountController(), "handle", String.class);

        Annotation[] annotations = handlerMethod.getMethodParameters()[0].getParameterAnnotations();

        RequestParam requestParam = Arrays.stream(annotations)
                .filter(RequestParam.class::isInstance)
                .map(RequestParam.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(requestParam.value()).isEqualTo("accountId");
        assertThat(requestParam.required()).isTrue();
    }

    @Test
    void resolvesComposedHttpMappingsAndResponseSemantics() throws Exception {
        Endpoint endpoint = new Endpoint();
        RequestMethod[] verbs = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE};
        String[] methods = {"read", "create", "update", "delete"};
        for (int i = 0; i < methods.length; i++) {
            HandlerMethod handler = new HandlerMethod(endpoint, methods[i], String.class, String.class, String.class);
            RequestMapping mapping = handler.getMethodAnnotation(RequestMapping.class);
            assertThat(mapping).isNotNull();
            assertThat(mapping.path()).containsExactly("/items/{id}");
            assertThat(mapping.method()).containsExactly(verbs[i]);
            Annotation rootMapping = MergedAnnotations.from(handler.getMethod()).get(Mapping.class)
                    .getRoot().synthesize();
            assertThat((String[]) AnnotationUtils.getValue(rootMapping)).containsExactly("/items/{id}");
        }
        HandlerMethod create = new HandlerMethod(endpoint, "create", String.class, String.class, String.class);
        assertThat(create.getMethodAnnotation(PostMapping.class).path()).containsExactly("/items/{id}");
        assertThat(create.getMethodAnnotation(ResponseStatus.class).code()).isEqualTo(HttpStatus.CREATED);
        assertThat(new HandlerMethod(endpoint, "read", String.class, String.class, String.class)
                .getMethodAnnotation(GetMapping.class).path()).containsExactly("/items/{id}");
        assertThat(new HandlerMethod(endpoint, "update", String.class, String.class, String.class)
                .getMethodAnnotation(PutMapping.class).path()).containsExactly("/items/{id}");
        assertThat(new HandlerMethod(endpoint, "delete", String.class, String.class, String.class)
                .getMethodAnnotation(DeleteMapping.class).path()).containsExactly("/items/{id}");
        assertThat(AnnotatedElementUtils.findMergedAnnotation(Endpoint.class, RestController.class).value())
                .isEqualTo("items");
        assertThat(AnnotatedElementUtils.hasAnnotation(Endpoint.class, ResponseBody.class)).isTrue();
    }

    @Test
    void exposesAliasedPathAndHeaderNamesAndRequiredRequestBody() throws Exception {
        HandlerMethod handler = new HandlerMethod(new Endpoint(), "create", String.class, String.class, String.class);
        MethodParameter[] parameters = handler.getMethodParameters();
        assertThat(parameters[0].getParameterAnnotation(PathVariable.class).name()).isEqualTo("id");
        RequestHeader header = parameters[1].getParameterAnnotation(RequestHeader.class);
        assertThat(header.name()).isEqualTo("X-Tenant");
        assertThat(header.required()).isFalse();
        assertThat(parameters[2].getParameterAnnotation(RequestBody.class).required()).isTrue();
    }

    @RestController("items")
    public static final class Endpoint {
        @GetMapping("/items/{id}")
        public String read(@PathVariable("id") String id, @RequestHeader(value = "X-Tenant", required = false) String tenant,
                @RequestBody String body) {
            return id;
        }

        @PostMapping("/items/{id}")
        @ResponseStatus(HttpStatus.CREATED)
        public String create(@PathVariable("id") String id, @RequestHeader(value = "X-Tenant", required = false) String tenant,
                @RequestBody String body) {
            return body;
        }

        @PutMapping("/items/{id}")
        public String update(String id, String tenant, String body) {
            return body;
        }

        @DeleteMapping("/items/{id}")
        public String delete(String id, String tenant, String body) {
            return id;
        }
    }

    private interface AccountControllerContract {

        void handle(@RequestParam("accountId") String accountId);
    }

    private static final class AccountController implements AccountControllerContract {

        @Override
        public void handle(String accountId) {
        }
    }
}
