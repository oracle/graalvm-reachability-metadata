/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_test.arquillian_test_api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.net.URL;
import java.util.Set;

import org.jboss.arquillian.test.api.ArquillianResource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class Arquillian_test_apiTest {
    @Test
    void defaultFieldResourceTypeIsTheAnnotationType() throws Exception {
        Field field = ResourceConsumer.class.getDeclaredField("defaultResource");

        ArquillianResource resource = field.getAnnotation(ArquillianResource.class);

        assertThat(resource).isNotNull();
        assertThat(resource.annotationType()).isEqualTo(ArquillianResource.class);
        assertThat(resource.value()).isEqualTo(ArquillianResource.class);
        assertThat(field.isAnnotationPresent(ArquillianResource.class)).isTrue();
    }

    @Test
    void fieldResourceTypeCanBeSpecialized() throws Exception {
        Field field = ResourceConsumer.class.getDeclaredField("urlResource");

        ArquillianResource resource = field.getAnnotation(ArquillianResource.class);

        assertThat(resource).isNotNull();
        assertThat(resource.value()).isEqualTo(URL.class);
    }

    @Test
    void parameterResourcesExposeDefaultAndSpecializedTypes() throws Exception {
        Method method = ResourceConsumer.class.getDeclaredMethod("acceptResources", String.class, URI.class);
        Parameter defaultParameter = method.getParameters()[0];
        Parameter uriParameter = method.getParameters()[1];

        ArquillianResource defaultResource = defaultParameter.getAnnotation(ArquillianResource.class);
        ArquillianResource uriResource = uriParameter.getAnnotation(ArquillianResource.class);

        assertThat(defaultParameter.isAnnotationPresent(ArquillianResource.class)).isTrue();
        assertThat(defaultResource.value()).isEqualTo(ArquillianResource.class);
        assertThat(uriResource.value()).isEqualTo(URI.class);
    }

    @Test
    void constructorParametersCanDeclareArquillianResources() throws Exception {
        Constructor<ResourceConsumer> constructor = ResourceConsumer.class.getDeclaredConstructor(Object.class, URL.class);
        Parameter defaultParameter = constructor.getParameters()[0];
        Parameter urlParameter = constructor.getParameters()[1];

        ArquillianResource defaultResource = defaultParameter.getAnnotation(ArquillianResource.class);
        ArquillianResource urlResource = urlParameter.getAnnotation(ArquillianResource.class);

        assertThat(defaultResource).isNotNull();
        assertThat(defaultResource.value()).isEqualTo(ArquillianResource.class);
        assertThat(urlResource).isNotNull();
        assertThat(urlResource.value()).isEqualTo(URL.class);
    }

    @Test
    void annotationContractAllowsRuntimeLookupOnFieldsAndParametersOnly() {
        Retention retention = ArquillianResource.class.getAnnotation(Retention.class);
        Target target = ArquillianResource.class.getAnnotation(Target.class);

        assertThat(retention).isNotNull();
        assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
        assertThat(target).isNotNull();
        assertThat(Set.of(target.value())).containsExactlyInAnyOrder(ElementType.FIELD, ElementType.PARAMETER);
        assertThat(ArquillianResource.class).hasAnnotation(Documented.class);
        assertThat(ArquillianResource.class).hasAnnotation(Inherited.class);
    }

    @Test
    void valueElementDeclaresTheSameDefaultUsedByAnnotationInstances() throws Exception {
        Method valueElement = ArquillianResource.class.getMethod("value");
        Field field = ResourceConsumer.class.getDeclaredField("defaultResource");
        ArquillianResource resource = field.getAnnotation(ArquillianResource.class);

        assertThat(valueElement.getReturnType()).isEqualTo(Class.class);
        assertThat(valueElement.getDefaultValue()).isEqualTo(ArquillianResource.class);
        assertThat(resource.value()).isEqualTo(valueElement.getDefaultValue());
    }

    @Test
    void annotationInstancesFollowStandardValueBasedEquality() throws Exception {
        ArquillianResource firstUrlResource = annotationOnField("firstUrlResource");
        ArquillianResource secondUrlResource = annotationOnField("secondUrlResource");
        ArquillianResource uriResource = annotationOnField("uriResource");

        assertThat(firstUrlResource).isEqualTo(secondUrlResource);
        assertThat(firstUrlResource.hashCode()).isEqualTo(secondUrlResource.hashCode());
        assertThat(firstUrlResource).isNotEqualTo(uriResource);
        assertThat(firstUrlResource.toString()).contains(ArquillianResource.class.getName());
        assertThat(firstUrlResource.toString()).contains(URL.class.getName());
    }

    @Test
    void unannotatedMembersRemainUnmarked() throws Exception {
        Field field = ResourceConsumer.class.getDeclaredField("plainResource");
        Method method = ResourceConsumer.class.getDeclaredMethod("acceptPlainResource", Object.class);
        Parameter parameter = method.getParameters()[0];

        assertThat(field.getAnnotation(ArquillianResource.class)).isNull();
        assertThat(field.isAnnotationPresent(ArquillianResource.class)).isFalse();
        assertThat(parameter.getAnnotation(ArquillianResource.class)).isNull();
        assertThat(parameter.isAnnotationPresent(ArquillianResource.class)).isFalse();
    }

    private static ArquillianResource annotationOnField(String fieldName) throws Exception {
        Field field = ResourceConsumer.class.getDeclaredField(fieldName);
        return field.getAnnotation(ArquillianResource.class);
    }

    private static final class ResourceConsumer {
        @ArquillianResource
        private String defaultResource;

        @ArquillianResource(URL.class)
        private URL urlResource;

        @ArquillianResource(URL.class)
        private URL firstUrlResource;

        @ArquillianResource(URL.class)
        private URL secondUrlResource;

        @ArquillianResource(URI.class)
        private URI uriResource;

        private Object plainResource;

        private ResourceConsumer(@ArquillianResource Object defaultValue, @ArquillianResource(URL.class) URL url) {
        }

        private void acceptResources(@ArquillianResource String defaultValue, @ArquillianResource(URI.class) URI uri) {
        }

        private void acceptPlainResource(Object value) {
        }
    }
}
