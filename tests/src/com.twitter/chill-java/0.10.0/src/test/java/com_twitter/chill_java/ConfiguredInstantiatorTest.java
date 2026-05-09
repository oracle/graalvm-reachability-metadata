/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twitter.chill_java;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.Kryo;
import com.twitter.chill.KryoInstantiator;
import com.twitter.chill.config.Config;
import com.twitter.chill.config.ConfiguredInstantiator;
import com.twitter.chill.config.JavaMapConfig;
import org.junit.jupiter.api.Test;

public class ConfiguredInstantiatorTest {
    private static final String REGISTRATION_REQUIRED_KEY = "configured.registration.required";

    @Test
    void createsDelegateWithConfigConstructorFromConfiguredClassName() throws Exception {
        JavaMapConfig config = new JavaMapConfig();
        config.setBoolean(REGISTRATION_REQUIRED_KEY, true);
        ConfiguredInstantiator.setReflect(config, ConfigConstructorInstantiator.class);

        ConfiguredInstantiator instantiator = new ConfiguredInstantiator(config);

        assertThat(instantiator.getDelegate()).isInstanceOf(ConfigConstructorInstantiator.class);
        Kryo kryo = instantiator.newKryo();
        assertThat(kryo.isRegistrationRequired()).isTrue();
    }

    @Test
    void fallsBackToNoArgDelegateWhenConfigConstructorIsAbsent() throws Exception {
        JavaMapConfig config = new JavaMapConfig();
        ConfiguredInstantiator.setReflect(config, NoArgInstantiator.class);

        ConfiguredInstantiator instantiator = new ConfiguredInstantiator(config);

        assertThat(instantiator.getDelegate()).isInstanceOf(NoArgInstantiator.class);
        Kryo kryo = instantiator.newKryo();
        assertThat(kryo.getReferences()).isFalse();
    }

    public static final class ConfigConstructorInstantiator extends KryoInstantiator {
        private final boolean registrationRequired;

        public ConfigConstructorInstantiator(Config config) {
            this.registrationRequired = config.getBoolean(REGISTRATION_REQUIRED_KEY, false);
        }

        @Override
        public Kryo newKryo() {
            Kryo kryo = super.newKryo();
            kryo.setRegistrationRequired(registrationRequired);
            return kryo;
        }
    }

    public static final class NoArgInstantiator extends KryoInstantiator {
        public NoArgInstantiator() {
        }

        @Override
        public Kryo newKryo() {
            Kryo kryo = super.newKryo();
            kryo.setReferences(false);
            return kryo;
        }
    }
}
