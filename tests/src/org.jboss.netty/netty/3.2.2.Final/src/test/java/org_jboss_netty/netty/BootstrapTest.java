/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_netty.netty;

import java.util.AbstractMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.ChannelHandler;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BootstrapTest {
    @Test
    void acceptsOrderedMapImplementationsWithPublicNoArgConstructors() {
        ClientBootstrap bootstrap = new ClientBootstrap();
        InsertionOrderPipelineMap pipelineMap = new InsertionOrderPipelineMap();
        pipelineMap.delegate.put("first", new NoopHandler());
        pipelineMap.delegate.put("second", new NoopHandler());

        @SuppressWarnings("unchecked")
        Map<String, ChannelHandler> typedPipelineMap = (Map<String, ChannelHandler>) (Map<?, ?>) pipelineMap;
        bootstrap.setPipelineAsMap(typedPipelineMap);

        assertThat(bootstrap.getPipelineAsMap()).containsOnlyKeys("first", "second");
    }

    @SuppressWarnings("rawtypes")
    public static final class InsertionOrderPipelineMap extends AbstractMap {
        private final LinkedHashMap<Object, Object> delegate = new LinkedHashMap<Object, Object>();

        @Override
        public Object put(Object key, Object value) {
            return delegate.put(key, value);
        }

        @Override
        public Set<Map.Entry<Object, Object>> entrySet() {
            return delegate.entrySet();
        }
    }

    public static final class NoopHandler extends SimpleChannelHandler {
    }
}
