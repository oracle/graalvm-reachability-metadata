/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_multipart_provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.mock.MockDispatcherFactory;
import org.jboss.resteasy.mock.MockHttpRequest;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.plugins.providers.StringTextStar;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormAnnotationReader;
import org.jboss.resteasy.spi.Dispatcher;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.junit.jupiter.api.Test;

public class MultipartFormAnnotationReaderTest {
    private static final MediaType CONTENT_TYPE = MediaType.valueOf("multipart/form-data; boundary=form-boundary");

    @Test
    void bindsPrivateAndInheritedFieldsAndPublicSetters() throws Exception {
        String body = """
                --form-boundary
                Content-Disposition: form-data; name="name"
                Content-Type: text/plain; charset=UTF-8

                Ada Lovelace
                --form-boundary
                Content-Disposition: form-data; name="source"
                Content-Type: text/plain; charset=UTF-8

                conference
                --form-boundary
                Content-Disposition: form-data; name="description"
                Content-Type: text/plain; charset=UTF-8

                Analytical engine
                --form-boundary
                Content-Disposition: form-data; name="title"
                Content-Type: text/plain; charset=UTF-8

                Presenter
                --form-boundary
                Content-Disposition: form-data; name="alias"
                Content-Type: text/plain; charset=UTF-8

                ada
                --form-boundary--
                """;

        RegistrationForm form = readForm(body);

        assertThat(form.name).isEqualTo("Ada Lovelace");
        assertThat(form.getSource()).isEqualTo("conference");
        assertThat(form.description).isEqualTo("Analytical engine");
        assertThat(form.title).isEqualTo("Presenter");
        assertThat(form.alias).isEqualTo("ada");
        assertThat(form.titleUpdates).isEqualTo(1);
        assertThat(form.aliasUpdates).isEqualTo(1);
        assertThat(form.missing).isEqualTo("not supplied");
    }

    @Test
    void leavesAbsentFormParametersAtTheirInitialValues() throws Exception {
        RegistrationForm form = readForm("""
                --form-boundary
                Content-Disposition: form-data; name="unrelated"
                Content-Type: text/plain; charset=UTF-8

                ignored
                --form-boundary--
                """);

        assertThat(form.name).isNull();
        assertThat(form.getSource()).isNull();
        assertThat(form.description).isNull();
        assertThat(form.title).isNull();
        assertThat(form.alias).isNull();
        assertThat(form.titleUpdates).isZero();
        assertThat(form.aliasUpdates).isZero();
        assertThat(form.missing).isEqualTo("not supplied");
    }

    private RegistrationForm readForm(String body) throws Exception {
        ResteasyProviderFactory previous = ResteasyProviderFactory.peekInstance();
        try (ResteasyContext.CloseableContext context = ResteasyContext.addCloseableContextDataLevel()) {
            Dispatcher dispatcher = MockDispatcherFactory.createDispatcher();
            dispatcher.getProviderFactory().registerProvider(StringTextStar.class);
            dispatcher.getProviderFactory().registerProvider(MultipartFormAnnotationReader.class);
            RegistrationResource resource = new RegistrationResource();
            dispatcher.getRegistry().addSingletonResource(resource);
            MockHttpRequest request = MockHttpRequest.post("/registration")
                    .contentType(CONTENT_TYPE)
                    .content(body.replace("\n", "\r\n").getBytes(StandardCharsets.UTF_8));
            MockHttpResponse response = new MockHttpResponse();
            dispatcher.invoke(request, response);
            assertThat(response.getStatus()).isEqualTo(204);
            assertThat(resource.form).isNotNull();
            return resource.form;
        } finally {
            ResteasyProviderFactory.setInstance(previous);
        }
    }

    @Path("/registration")
    public static class RegistrationResource {
        private RegistrationForm form;

        @POST
        @Consumes(MediaType.MULTIPART_FORM_DATA)
        public void register(@MultipartForm RegistrationForm form) {
            this.form = form;
        }
    }

    public static class BaseForm {
        @FormParam("source")
        private String source;

        public String getSource() {
            return source;
        }
    }

    public static class RegistrationForm extends BaseForm {
        @FormParam("name")
        private String name;

        @FormParam("description")
        private String description;

        @FormParam("missing")
        private String missing = "not supplied";

        private String title;
        private String alias;
        private int titleUpdates;
        private int aliasUpdates;

        @FormParam("title")
        public void setTitle(String title) {
            this.title = title;
            titleUpdates++;
        }

        @FormParam("alias")
        public void setAlias(String alias) {
            this.alias = alias;
            aliasUpdates++;
        }
    }
}
