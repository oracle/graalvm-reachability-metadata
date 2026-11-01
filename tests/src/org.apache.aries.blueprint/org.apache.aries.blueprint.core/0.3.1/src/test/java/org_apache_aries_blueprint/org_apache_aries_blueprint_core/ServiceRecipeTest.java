/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.aries.blueprint.container.BlueprintContainerImpl;
import org.apache.aries.blueprint.container.ServiceRecipe;
import org.apache.aries.blueprint.di.MapRecipe;
import org.apache.aries.blueprint.di.PassThroughRecipe;
import org.apache.aries.blueprint.reflect.RefMetadataImpl;
import org.apache.aries.blueprint.reflect.ServiceMetadataImpl;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import static org.assertj.core.api.Assertions.assertThat;

public class ServiceRecipeTest {
    @Test
    void publishesServicePropertiesAndWithdrawsRegistration() {
        TestBundleContext context = new TestBundleContext();
        BlueprintContainerImpl container = new BlueprintContainerImpl(
                context, context.getBundle(), null, null, null, List.of());
        AtomicInteger calls = new AtomicInteger();
        Runnable service = calls::incrementAndGet;
        ServiceMetadataImpl metadata = new ServiceMetadataImpl();
        metadata.setId("publishedTask");
        metadata.addInterface(Runnable.class.getName());
        metadata.setRanking(7);
        metadata.setServiceComponent(new RefMetadataImpl("task"));
        MapRecipe properties = new MapRecipe("serviceProperties", Map.class);
        properties.put(new PassThroughRecipe("regionKey", "region"), new PassThroughRecipe("regionValue", "east"));
        ServiceRecipe recipe = new ServiceRecipe("publishedTask", container, metadata,
                new PassThroughRecipe("task", service), null, properties, List.of());
        ServiceRegistration handle = (ServiceRegistration) RecipeSupport.create(container, recipe);
        try {
            recipe.register();
            assertThat(recipe.isRegistered()).isTrue();
            ServiceReference reference = context.getServiceReference(Runnable.class.getName());
            assertThat(handle.getReference()).isSameAs(reference);
            assertThat((String[]) reference.getProperty(Constants.OBJECTCLASS)).containsExactly(Runnable.class.getName());
            assertThat(reference.getProperty(Constants.SERVICE_RANKING)).isEqualTo(7);
            assertThat(reference.getProperty("region")).isEqualTo("east");
            assertThat(reference.getProperty("osgi.service.blueprint.compname")).isEqualTo("task");
            Runnable resolved = (Runnable) recipe.getService(context.getBundle(), null);
            assertThat(resolved).isSameAs(service);
            resolved.run();
            assertThat(calls.get()).isEqualTo(1);
            recipe.register();
            assertThat(context.getServiceReference(Runnable.class.getName())).isSameAs(reference);
        } finally {
            recipe.unregister();
        }
        assertThat(recipe.isRegistered()).isFalse();
        assertThat(context.getServiceReference(Runnable.class.getName())).isNull();
    }
}
