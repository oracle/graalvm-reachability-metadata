/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_sun_jersey.jersey_grizzly2;

import com.sun.jersey.api.container.grizzly2.GrizzlyWebContainerFactory;
import com.sun.jersey.spi.container.servlet.ServletContainer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class GrizzlyWebContainerFactoryTest {
    @Test
    public void createInstantiatesServletBeforeValidatingUriPath() {
        URI uriWithoutPath = URI.create("http://127.0.0.1:0");
        Map<String, String> initParams = Collections.emptyMap();

        assertThatThrownBy(() -> GrizzlyWebContainerFactory.create(uriWithoutPath, ServletContainer.class, initParams))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be present");
    }
}
