/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindConstructorProvider;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultBindConstructorProviderInnerConstructorsTest {

    @Test
    void updatesExistingInnerBeanWithoutTreatingItsEnclosingInstanceAsAProperty() {
        Account account = new Account();
        Account.Preferences preferences = account.new Preferences();
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of("preferences.locale", "fr")));

        assertThat(BindConstructorProvider.DEFAULT.getBindConstructor(preferences.getClass(), false)).isNull();
        Account.Preferences bound = binder.bind("preferences", Bindable.ofInstance(preferences)).get();

        assertThat(bound).isSameAs(preferences);
        assertThat(bound.getLocale()).isEqualTo("fr");
        assertThat(bound.getAccountName()).isEqualTo("operations");
    }

    public static class Account {

        private final String name = "operations";

        public class Preferences {

            private String locale = "en";

            public String getLocale() {
                return this.locale;
            }

            public void setLocale(String locale) {
                this.locale = locale;
            }

            public String getAccountName() {
                return Account.this.name;
            }
        }
    }
}
