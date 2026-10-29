/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.jboss.resteasy.spi.ContextInjector;

public class AsyncContextFixture implements ContextInjector<CompletionStage<AsyncContextFixture.Label>, AsyncContextFixture.Label> {
    @Override
    public CompletionStage<Label> resolve(Class<? extends CompletionStage<Label>> rawType, Type genericType, Annotation[] annotations) {
        return CompletableFuture.completedFuture(new Label("async-context"));
    }

    public static class Label {
        final String text;

        public Label(String text) {
            this.text = text;
        }
    }
}
