/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_multipart_provider;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.jboss.resteasy.annotations.providers.multipart.PartFilename;
import org.jboss.resteasy.annotations.providers.multipart.PartType;
import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.mock.MockDispatcherFactory;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.plugins.providers.StringTextStar;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormAnnotationWriter;
import org.jboss.resteasy.spi.Dispatcher;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.jupiter.api.Test;

public class MultipartFormAnnotationWriterTest {
    @Test
    void writesPrivateAndInheritedFieldsAndPublicGetters() throws Exception {
        ResteasyProviderFactory previous = ResteasyProviderFactory.peekInstance();
        try (ResteasyContext.CloseableContext context = ResteasyContext.addCloseableContextDataLevel()) {
            Dispatcher dispatcher = MockDispatcherFactory.createDispatcher();
            dispatcher.getProviderFactory().registerProvider(StringTextStar.class);
            dispatcher.getProviderFactory().registerProvider(MultipartFormAnnotationWriter.class);
            ExportResource resource = new ExportResource();
            dispatcher.getRegistry().addSingletonResource(resource);
            MockHttpRequest request = MockHttpRequest.get("/export")
                    .accept(MediaType.MULTIPART_FORM_DATA);
            MockHttpResponse response = new MockHttpResponse();

            dispatcher.invoke(request, response);

            assertThat(response.getStatus()).isEqualTo(200);
            String body = response.getContentAsString();
            assertPart(body, "name", "Ada Lovelace");
            assertPart(body, "source", "conference");
            assertPart(body, "description", "Analytical engine");
            assertPart(body, "category", "research");
            assertThat(body).contains("name=\"description\"; filename=\"description.txt\"")
                    .doesNotContain("name=\"ignored\"", "not a part", "name=\"absent\"")
                    .endsWith("--export-boundary--");
            assertThat(resource.form.descriptionReads).isEqualTo(1);
        } finally {
            ResteasyProviderFactory.setInstance(previous);
        }
    }

    private void assertPart(String body, String name, String value) {
        assertThat(body.split("--export-boundary"))
                .filteredOn(part -> part.contains("name=\"" + name + "\""))
                .singleElement()
                .satisfies(part -> assertThat(part)
                        .contains("Content-Type: text/plain")
                        .endsWith("\r\n\r\n" + value + "\r\n"));
    }

    @Path("/export")
    public static class ExportResource {
        private final ExportForm form = new ExportForm();

        @GET
        @Produces("multipart/form-data; boundary=export-boundary")
        @MultipartForm
        public ExportForm export() {
            return form;
        }
    }

    public static class BaseForm {
        @FormParam("source")
        @PartType(MediaType.TEXT_PLAIN)
        private String source = "conference";

        @FormParam("category")
        @PartType(MediaType.TEXT_PLAIN)
        public String getCategory() {
            return "research";
        }
    }

    public static class ExportForm extends BaseForm {
        @FormParam("name")
        @PartType(MediaType.TEXT_PLAIN)
        private String name = "Ada Lovelace";

        @FormParam("ignored")
        private String ignored = "not a part";

        @FormParam("absent")
        @PartType(MediaType.TEXT_PLAIN)
        private String absent;

        private int descriptionReads;

        @FormParam("description")
        @PartType(MediaType.TEXT_PLAIN)
        @PartFilename("description.txt")
        public String getDescription() {
            descriptionReads++;
            return "Analytical engine";
        }
    }
}
