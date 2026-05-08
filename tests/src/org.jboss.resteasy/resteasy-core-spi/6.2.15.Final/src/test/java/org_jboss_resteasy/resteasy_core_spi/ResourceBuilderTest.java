/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core_spi;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;

import org.jboss.resteasy.spi.metadata.FieldParameter;
import org.jboss.resteasy.spi.metadata.Parameter;
import org.jboss.resteasy.spi.metadata.ResourceBuilder;
import org.jboss.resteasy.spi.metadata.ResourceClass;
import org.jboss.resteasy.spi.metadata.SetterParameter;
import org.junit.jupiter.api.Test;

public class ResourceBuilderTest {
    @Test
    void buildsResourceMetadataFromDeclaredFieldsSettersAndPublicMethods() {
        ResourceClass resourceClass = new ResourceBuilder()
                .getRootResourceFromAnnotations(AnnotatedInjectionResource.class);

        assertThat(resourceClass.getClazz()).isEqualTo(AnnotatedInjectionResource.class);
        assertThat(resourceClass.getPath()).isEqualTo("resources");
        assertThat(resourceClass.getFields()).hasSize(1);
        assertThat(resourceClass.getSetters()).hasSize(1);
        assertThat(resourceClass.getResourceMethods()).isEmpty();
        assertThat(resourceClass.getResourceLocators()).isEmpty();

        FieldParameter fieldParameter = resourceClass.getFields()[0];
        assertThat(fieldParameter.getParamType()).isEqualTo(Parameter.ParamType.QUERY_PARAM);
        assertThat(fieldParameter.getParamName()).isEqualTo("filter");
        assertThat(fieldParameter.getType()).isEqualTo(String.class);
        assertThat(fieldParameter.getField().getName()).isEqualTo("filter");

        SetterParameter setterParameter = resourceClass.getSetters()[0];
        assertThat(setterParameter.getParamType()).isEqualTo(Parameter.ParamType.HEADER_PARAM);
        assertThat(setterParameter.getParamName()).isEqualTo("X-Trace-Id");
        assertThat(setterParameter.getType()).isEqualTo(String.class);
        assertThat(setterParameter.getSetter().getName()).isEqualTo("setTraceId");
    }

    @Path("resources")
    public static class AnnotatedInjectionResource {
        @QueryParam("filter")
        private String filter;

        private String traceId;

        public String getFilter() {
            return filter;
        }

        public String getTraceId() {
            return traceId;
        }

        @HeaderParam("X-Trace-Id")
        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }
    }
}
