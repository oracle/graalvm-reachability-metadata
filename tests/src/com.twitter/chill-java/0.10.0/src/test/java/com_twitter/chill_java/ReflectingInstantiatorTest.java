/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_twitter.chill_java;

import static org.assertj.core.api.Assertions.assertThat;

import com.esotericsoftware.kryo.Kryo;
import com.twitter.chill.KryoPool;
import com.twitter.chill.config.JavaMapConfig;
import com.twitter.chill.config.ReflectingInstantiator;
import com.twitter.chill.java.RegexSerializer;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

public class ReflectingInstantiatorTest {
    @Test
    void createsConfiguredKryoAndAppliesReflectiveRegistrations() throws Exception {
        JavaMapConfig config = new JavaMapConfig(new HashMap<String, String>());
        config.set(ReflectingInstantiator.KRYO_CLASS, Kryo.class.getName());
        config.set(ReflectingInstantiator.INSTANTIATOR_STRATEGY_CLASS,
                "org.objenesis.strategy.StdInstantiatorStrategy");
        config.set(ReflectingInstantiator.REGISTRATIONS,
                String.class.getName() + ":" + Pattern.class.getName()
                        + "," + RegexSerializer.class.getName());

        ReflectingInstantiator instantiator = new ReflectingInstantiator(config);
        KryoPool pool = KryoPool.withByteArrayOutputStream(2, instantiator);

        assertThat(pool.hasRegistration(String.class)).isTrue();
        assertThat(pool.hasRegistration(Pattern.class)).isTrue();

        Pattern original = Pattern.compile("chill-[a-z]+-configured");
        byte[] serialized = pool.toBytesWithoutClass(original);

        assertThat(pool.fromBytes(serialized, Pattern.class).pattern())
                .isEqualTo(original.pattern());

        Map<String, String> roundTripConfig = new HashMap<>();
        instantiator.set(new JavaMapConfig(roundTripConfig));
        assertThat(roundTripConfig).containsEntry(ReflectingInstantiator.REGISTRATIONS,
                config.get(ReflectingInstantiator.REGISTRATIONS));
    }

}
