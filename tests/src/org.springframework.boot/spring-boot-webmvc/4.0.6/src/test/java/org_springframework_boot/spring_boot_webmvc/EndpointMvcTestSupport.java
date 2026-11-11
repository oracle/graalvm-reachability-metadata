/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_webmvc;

import java.util.Collection;
import java.util.List;

import org.springframework.boot.actuate.endpoint.Access;
import org.springframework.boot.actuate.endpoint.EndpointId;
import org.springframework.boot.actuate.endpoint.InvocationContext;
import org.springframework.boot.actuate.endpoint.OperationType;
import org.springframework.boot.actuate.endpoint.web.EndpointLinksResolver;
import org.springframework.boot.actuate.endpoint.web.EndpointMapping;
import org.springframework.boot.actuate.endpoint.web.EndpointMediaTypes;
import org.springframework.boot.actuate.endpoint.web.ExposableWebEndpoint;
import org.springframework.boot.actuate.endpoint.web.WebEndpointHttpMethod;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.WebOperation;
import org.springframework.boot.actuate.endpoint.web.WebOperationRequestPredicate;
import org.springframework.boot.webmvc.actuate.endpoint.web.WebMvcEndpointHandlerMapping;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;

final class EndpointMvcTestSupport {
    private EndpointMvcTestSupport() {
    }

    static GenericWebApplicationContext createContext(boolean registerLinks) {
        GenericWebApplicationContext context = new GenericWebApplicationContext(new MockServletContext());
        List<ExposableWebEndpoint> endpoints = List.of(new OrdersEndpoint());
        context.registerBean("endpointHandlerMapping", WebMvcEndpointHandlerMapping.class,
                () -> new WebMvcEndpointHandlerMapping(new EndpointMapping("/manage"), endpoints,
                        EndpointMediaTypes.DEFAULT, null, new EndpointLinksResolver(endpoints), registerLinks));
        context.registerBean("handlerAdapter", RequestMappingHandlerAdapter.class, () -> {
            RequestMappingHandlerAdapter adapter = new RequestMappingHandlerAdapter();
            adapter.setMessageConverters(List.of(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter()));
            return adapter;
        });
        context.refresh();
        return context;
    }

    private static final class OrdersEndpoint implements ExposableWebEndpoint {
        @Override
        public EndpointId getEndpointId() {
            return EndpointId.of("orders");
        }

        @Override
        public Access getDefaultAccess() {
            return Access.READ_ONLY;
        }

        @Override
        public String getRootPath() {
            return "orders";
        }

        @Override
        public Collection<WebOperation> getOperations() {
            return List.of(new OrderOperation());
        }
    }

    private static final class OrderOperation implements WebOperation {
        @Override
        public String getId() {
            return "orders-order";
        }

        @Override
        public boolean isBlocking() {
            return true;
        }

        @Override
        public OperationType getType() {
            return OperationType.READ;
        }

        @Override
        public WebOperationRequestPredicate getRequestPredicate() {
            return new WebOperationRequestPredicate("orders/{order}", WebEndpointHttpMethod.GET,
                    List.of(), List.of(MediaType.TEXT_PLAIN_VALUE));
        }

        @Override
        public Object invoke(InvocationContext context) {
            String order = (String) context.getArguments().get("order");
            String detail = (String) context.getArguments().get("detail");
            return new WebEndpointResponse<>("Order " + order + ": " + detail,
                    HttpStatus.ACCEPTED.value(), MediaType.TEXT_PLAIN);
        }
    }
}
