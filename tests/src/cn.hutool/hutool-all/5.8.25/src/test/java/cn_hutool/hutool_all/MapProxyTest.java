/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.map.MapProxy;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class MapProxyTest {

    @Test
    void createsInterfaceProxyBeanBackedByMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", "Ada");
        values.put("active", "true");
        values.put("score", "42");
        values.put("favorite_color", "blue");

        ProfileView profile = MapProxy.create(values).toProxyBean(ProfileView.class);

        assertThat(profile.getName()).isEqualTo("Ada");
        assertThat(profile.isActive()).isTrue();
        assertThat(profile.getScore()).isEqualTo(42);
        assertThat(profile.getFavoriteColor()).isEqualTo("blue");

        ProfileView returnedProfile = profile.setName("Grace");
        assertThat(returnedProfile).isSameAs(profile);
        assertThat(profile.getName()).isEqualTo("Grace");
        assertThat(values).containsEntry("name", "Grace");
    }

    public interface ProfileView {
        String getName();

        boolean isActive();

        int getScore();

        String getFavoriteColor();

        ProfileView setName(String name);
    }
}
