/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twitter.chill_java;

import static org.assertj.core.api.Assertions.assertThat;

import com.twitter.chill.KryoInstantiator;
import com.twitter.chill.KryoPool;
import com.twitter.chill.config.ConfiguredInstantiator;
import com.twitter.chill.config.JavaMapConfig;
import com.twitter.chill.config.ReflectingInstantiator;
import com.twitter.chill.java.PackageRegistrar;
import com.twitter.chill.java.RegexSerializer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

public class ConfiguredInstantiatorTest {
    @Test
    void reflectsDelegateClassWithConfigConstructor() throws Exception {
        JavaMapConfig config = new JavaMapConfig(new HashMap<String, String>());
        ConfiguredInstantiator.setReflect(config, ReflectingInstantiator.class);
        config.set(ReflectingInstantiator.REGISTRATIONS,
                String.class.getName() + ":" + Pattern.class.getName()
                        + "," + RegexSerializer.class.getName());

        ConfiguredInstantiator instantiator = new ConfiguredInstantiator(config);
        KryoPool pool = KryoPool.withByteArrayOutputStream(2, instantiator);

        assertThat(instantiator.getDelegate()).isInstanceOf(ReflectingInstantiator.class);
        assertThat(pool.hasRegistration(String.class)).isTrue();
        assertThat(pool.hasRegistration(Pattern.class)).isTrue();

        Pattern original = Pattern.compile("configured-[a-z]+-constructor");
        byte[] serialized = pool.toBytesWithoutClass(original);

        assertThat(pool.fromBytes(serialized, Pattern.class).pattern())
                .isEqualTo(original.pattern());
    }

    @Test
    void reflectsDelegateClassWithDefaultConstructor() throws Exception {
        JavaMapConfig config = new JavaMapConfig(new HashMap<String, String>());
        ConfiguredInstantiator.setReflect(config, KryoInstantiator.class);

        ConfiguredInstantiator instantiator = new ConfiguredInstantiator(config);
        KryoInstantiator javaInstantiator = instantiator.withRegistrar(PackageRegistrar.all());
        KryoPool pool = KryoPool.withByteArrayOutputStream(2, javaInstantiator);
        List<String> original = Arrays.asList("chill", "configured", "instantiator");

        assertThat(instantiator.getDelegate()).isExactlyInstanceOf(KryoInstantiator.class);
        assertThat(pool.hasRegistration(original.getClass())).isTrue();

        byte[] serialized = pool.toBytesWithClass(original);

        assertThat(pool.fromBytes(serialized)).isEqualTo(original);
    }

}
