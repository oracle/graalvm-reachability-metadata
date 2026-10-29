/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.xml.bind.annotation.XmlEnum;

import org.jboss.resteasy.annotations.StringParameterUnmarshallerBinder;
import org.jboss.resteasy.mock.MockHttpResponse;
import org.jboss.resteasy.spi.StringParameterUnmarshaller;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StringParameterInjectorTest {
    @Test
    void convertsConstructorsFactoriesArraysCollectionsAndBoundUnmarshallers() throws Exception {
        try (DispatcherSession session = new DispatcherSession()) {
            session.dispatcher.getRegistry().addPerRequestResource(ConversionResource.class);
            MockHttpResponse response = session.get("/convert?ctor=oak&factory=elm&from=ash&array=3&array=5&list=red&list=blue&set=x&set=x&bound=birch&xml=green");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentAsString()).isEqualTo("oak:elm:ash:8:red,blue:1:BIRCH:GREEN");
        }
    }

    public static class Constructed {
        final String text;

        public Constructed(String text) {
            this.text = text;
        }
    }

    public static class FactoryValue {
        final String text;

        private FactoryValue(String text) {
            this.text = text;
        }

        public static FactoryValue valueOf(String text) {
            return new FactoryValue(text);
        }
    }

    public static class FromStringValue {
        final String text;

        private FromStringValue(String text) {
            this.text = text;
        }

        public static FromStringValue fromString(String text) {
            return new FromStringValue(text);
        }
    }

    @XmlEnum
    public enum Color {
        GREEN;

        public static Color fromValue(String text) {
            return valueOf(text.toUpperCase(Locale.ROOT));
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @StringParameterUnmarshallerBinder(UppercaseUnmarshaller.class)
    public @interface Uppercase {
    }

    public static class UppercaseUnmarshaller implements StringParameterUnmarshaller<Constructed> {
        @Override
        public void setAnnotations(Annotation[] annotations) {
            // This converter has no configurable attributes.
        }

        @Override
        public Constructed fromString(String text) {
            return new Constructed(text.toUpperCase(Locale.ROOT));
        }
    }

    @Path("/convert")
    public static class ConversionResource {
        @GET
        public String get(@QueryParam("ctor") Constructed ctor,
                @QueryParam("factory") FactoryValue factory,
                @QueryParam("from") FromStringValue from,
                @QueryParam("array") Integer[] array,
                @QueryParam("list") List<String> list,
                @QueryParam("set") Set<String> set,
                @Uppercase @QueryParam("bound") Constructed bound,
                @QueryParam("xml") Color color) {
            return ctor.text + ":" + factory.text + ":" + from.text + ":" + (array[0] + array[1])
                    + ":" + list.stream().collect(Collectors.joining(",")) + ":" + set.size() + ":" + bound.text + ":" + color;
        }
    }
}
