/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twitter.chill_java;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.Kryo;
import com.twitter.chill.config.JavaMapConfig;
import com.twitter.chill.config.ReflectingInstantiator;
import com.twitter.chill.java.LocaleSerializer;
import java.util.HashMap;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class ReflectingInstantiatorTest {
    @Test
    void createsKryoFromReflectiveConfigAndAppliesRegistrars() throws Exception {
        JavaMapConfig config = new JavaMapConfig();
        config.set(ReflectingInstantiator.REGISTRATIONS, String.join(":",
                HashMap.class.getName(),
                Locale.class.getName() + "," + LocaleSerializer.class.getName()));
        config.set(ReflectingInstantiator.DEFAULT_REGISTRATIONS,
                CharSequence.class.getName() + "," + LocaleSerializer.class.getName());

        ReflectingInstantiator instantiator = new ReflectingInstantiator(config);
        JavaMapConfig capturedConfig = new JavaMapConfig();
        instantiator.set(capturedConfig);

        Kryo kryo = instantiator.newKryo();
        assertThat(kryo).isInstanceOf(Kryo.class);
        assertThat(kryo.isRegistrationRequired()).isFalse();
        assertThat(kryo.getInstantiatorStrategy()).isNotNull();
        assertThat(kryo.getRegistration(HashMap.class)).isNotNull();
        assertThat(kryo.getRegistration(Locale.class).getSerializer()).isInstanceOf(LocaleSerializer.class);
        assertThat(capturedConfig.get(ReflectingInstantiator.REGISTRATIONS)).isEqualTo(String.join(":",
                HashMap.class.getName(),
                Locale.class.getName() + "," + LocaleSerializer.class.getName()));
        assertThat(capturedConfig.get(ReflectingInstantiator.DEFAULT_REGISTRATIONS))
                .isEqualTo(CharSequence.class.getName() + "," + LocaleSerializer.class.getName());
    }
}
