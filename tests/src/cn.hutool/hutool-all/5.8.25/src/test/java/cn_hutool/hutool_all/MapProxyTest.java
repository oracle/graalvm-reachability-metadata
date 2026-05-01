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
    public void createsProxyBeanThatReadsValuesFromMap() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", "Hutool");
        values.put("age", "21");
        values.put("active", "true");
        values.put("given_name", "Core");

        PersonView person = MapProxy.create(values).toProxyBean(PersonView.class);

        assertThat(person.getName()).isEqualTo("Hutool");
        assertThat(person.getAge()).isEqualTo(21);
        assertThat(person.isActive()).isTrue();
        assertThat(person.getGivenName()).isEqualTo("Core");
    }

    @Test
    public void proxyBeanSetterWritesBackToMapAndReturnsProxy() {
        Map<String, Object> values = new LinkedHashMap<>();
        PersonView person = MapProxy.create(values).toProxyBean(PersonView.class);

        PersonView returned = person.setNickname("toolkit");

        assertThat(returned).isSameAs(person);
        assertThat(values).containsEntry("nickname", "toolkit");
        assertThat(person.getNickname()).isEqualTo("toolkit");
    }

    public interface PersonView {
        String getName();

        int getAge();

        boolean isActive();

        String getGivenName();

        String getNickname();

        PersonView setNickname(String nickname);
    }
}
