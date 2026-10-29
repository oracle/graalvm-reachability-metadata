/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import java.nio.charset.StandardCharsets;

import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FormInjectorTest {
    @Test
    void constructsAndPopulatesFormBean() throws Exception {
        try (DispatcherSession session = new DispatcherSession()) {
            session.dispatcher.getRegistry().addPerRequestResource(FormResource.class);
            MockHttpResponse response = new MockHttpResponse();
            MockHttpRequest request = MockHttpRequest.post("/forms").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .content("name=Grace&quantity=4".getBytes(StandardCharsets.UTF_8));
            session.dispatcher.invoke(request, response);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEqualTo("Grace:4");
        }
    }

    public static class OrderForm {
        @FormParam("name")
        private String name;
        @FormParam("quantity")
        private int quantity;
    }

    @Path("/forms")
    public static class FormResource {
        @POST
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        public String post(@BeanParam OrderForm form) {
            return form.name + ":" + form.quantity;
        }
    }
}
