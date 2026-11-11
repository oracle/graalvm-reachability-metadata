/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_webmvc;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.GenericWebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

public class AbstractWebMvcEndpointHandlerMappingInnerLinksHandlerTest {
    @Test
    void servesDiscoveryLinksThroughTheRegisteredMvcHandler() throws Exception {
        try (GenericWebApplicationContext context = EndpointMvcTestSupport.createContext(true)) {
            MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();

            MockHttpServletResponse response = mvc.perform(get("/manage").accept(MediaType.APPLICATION_JSON))
                    .andReturn().getResponse();

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
            JsonNode links = JsonMapper.builder().build().readTree(response.getContentAsString()).path("_links");
            assertThat(links.path("self").path("href").asString()).isEqualTo("http://localhost/manage");
            assertThat(links.size()).isEqualTo(2);
            assertThat(links.path("orders-order").path("href").asString())
                    .isEqualTo("http://localhost/manage/orders/{order}");
            assertThat(links.path("orders-order").path("templated").asBoolean()).isTrue();
        }
    }
}
