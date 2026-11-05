/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class ValueObjectBinderTest {

    @Test
    void suppliesEmptyTypedArraysForOmittedConstructorProperties() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("worker.name", "mailer")));

        Worker worker = binder.bind("worker", Bindable.of(Worker.class)).get();

        assertThat(worker.name()).isEqualTo("mailer");
        assertThat(worker.recipients()).isNotNull().isEmpty();
        assertThat(worker.retryDelays()).isNotNull().isEmpty();
    }

    public record Worker(String name, @DefaultValue String[] recipients, @DefaultValue int[] retryDelays) {
    }
}
