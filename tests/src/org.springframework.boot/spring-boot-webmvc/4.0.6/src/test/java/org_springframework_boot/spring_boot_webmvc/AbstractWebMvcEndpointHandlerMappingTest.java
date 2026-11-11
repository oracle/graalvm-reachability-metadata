/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_webmvc;

import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.GenericWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class AbstractWebMvcEndpointHandlerMappingTest {
    @Test
    void dispatchesAnOperationWithPathAndQueryArgumentsAndItsResponseStatus() throws Exception {
        try (GenericWebApplicationContext context = EndpointMvcTestSupport.createContext(false)) {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();

            MockHttpServletResponse response = mvc.perform(get("/manage/orders/42")
                    .param("detail", "shipment").accept(MediaType.TEXT_PLAIN)).andReturn().getResponse();

            assertThat(response.getStatus()).isEqualTo(202);
            assertThat(response.getContentType()).startsWith(MediaType.TEXT_PLAIN_VALUE);
            assertThat(response.getContentAsString()).isEqualTo("Order 42: shipment");
        }
    }
}
