/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core;

import jakarta.ws.rs.GET;

import org.jboss.resteasy.util.GetRestful;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class GetRestfulTest {
    @Test
    void locatesSubresourcesDeclaredOnClassesAndInterfaces() {
        assertThat(GetRestful.isSubResourceClass(ConcreteSubresource.class)).isTrue();
        assertThat(GetRestful.getSubResourceClass(ConcreteSubresource.class)).isEqualTo(ConcreteSubresource.class);
        assertThat(GetRestful.isSubResourceClass(InterfaceSubresource.class)).isTrue();
        assertThat(GetRestful.getSubResourceClasses(InterfaceSubresource.class)).containsExactly(SubresourceApi.class);
        assertThat(GetRestful.isSubResourceClass(Object.class)).isFalse();
    }

    public static class ConcreteSubresource {
        @GET
        public String get() {
            return "concrete";
        }
    }

    public interface SubresourceApi {
        @GET
        String get();
    }

    public static class InterfaceSubresource implements SubresourceApi {
        @Override
        public String get() {
            return "interface";
        }
    }
}
