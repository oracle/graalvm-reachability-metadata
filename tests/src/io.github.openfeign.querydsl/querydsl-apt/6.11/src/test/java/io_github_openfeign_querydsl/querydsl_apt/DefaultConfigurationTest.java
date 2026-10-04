/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_openfeign_querydsl.querydsl_apt;

import com.querydsl.apt.DefaultConfiguration;
import com.querydsl.codegen.DefaultVariableNameFunction;
import com.querydsl.core.annotations.QueryEmbeddable;
import com.querydsl.core.annotations.QueryEmbedded;
import com.querydsl.core.annotations.QueryEntities;
import com.querydsl.core.annotations.QueryEntity;
import com.querydsl.core.annotations.QuerySupertype;
import com.querydsl.core.annotations.QueryTransient;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultConfigurationTest {
    @Test
    void configurationLoadsConfiguredVariableNameFunction() {
        DefaultConfiguration configuration = new DefaultConfiguration(
                EMPTY_ROUND_ENVIRONMENT,
                Map.of(
                        "querydsl.variableNameFunctionClass",
                        "com.querydsl.codegen.DefaultVariableNameFunction"),
                Set.of(),
                QueryEntities.class,
                QueryEntity.class,
                QuerySupertype.class,
                QueryEmbeddable.class,
                QueryEmbedded.class,
                QueryTransient.class);

        assertThat(configuration.getVariableNameFunction())
                .isInstanceOf(DefaultVariableNameFunction.class);
    }

    private static final RoundEnvironment EMPTY_ROUND_ENVIRONMENT = new RoundEnvironment() {
        @Override
        public boolean processingOver() {
            return false;
        }

        @Override
        public boolean errorRaised() {
            return false;
        }

        @Override
        public Set<? extends Element> getRootElements() {
            return Set.of();
        }

        @Override
        public Set<? extends Element> getElementsAnnotatedWith(TypeElement annotationType) {
            return Set.of();
        }

        @Override
        public Set<? extends Element> getElementsAnnotatedWith(Class<? extends Annotation> annotationType) {
            return Set.of();
        }
    };

    @Test
    void annotationProcessorGeneratesQueryTypeAndPaths() {
        QCustomer customer = QCustomer.customer;

        assertThat(customer.getType()).isEqualTo(Customer.class);
        assertThat(customer.name.getMetadata().getName()).isEqualTo("name");
        assertThat(customer.name.eq("Ada").toString()).contains("customer.name", "Ada");
    }
}

@QueryEntity
class Customer {
    private String name;

    public String getName() {
        return name;
    }
}
