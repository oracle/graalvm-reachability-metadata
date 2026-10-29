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

import jakarta.ws.rs.core.MediaType;

import org.jboss.resteasy.annotations.DecorateTypes;
import org.jboss.resteasy.annotations.Decorator;
import org.jboss.resteasy.core.interception.jaxrs.DecoratorMatcher;
import org.jboss.resteasy.spi.DecoratorProcessor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DecoratorMatcherTest {
    @Test
    void instantiatesAnnotationSelectedProcessorForMatchingMediaType() {
        DecoratorMatcher matcher = new DecoratorMatcher();
        StringBuilder matching = new StringBuilder("message");
        StringBuilder decorated = matcher.decorate(StringBuilder.class, matching, DecoratedBody.class,
                new Annotation[0], MediaType.TEXT_PLAIN_TYPE);
        assertThat(decorated.toString()).isEqualTo("message!");
        StringBuilder other = new StringBuilder("message");
        assertThat(matcher.decorate(StringBuilder.class, other, DecoratedBody.class,
                new Annotation[0], MediaType.APPLICATION_JSON_TYPE).toString()).isEqualTo("message");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Decorator(processor = SuffixProcessor.class, target = StringBuilder.class)
    public @interface Suffix {
        String value();
    }

    @Suffix("!")
    public static class DecoratedBody {
    }

    @DecorateTypes("text/plain")
    public static class SuffixProcessor implements DecoratorProcessor<StringBuilder, Suffix> {
        @Override
        public StringBuilder decorate(StringBuilder target, Suffix annotation, Class type,
                Annotation[] annotations, MediaType mediaType) {
            return target.append(annotation.value());
        }
    }
}
