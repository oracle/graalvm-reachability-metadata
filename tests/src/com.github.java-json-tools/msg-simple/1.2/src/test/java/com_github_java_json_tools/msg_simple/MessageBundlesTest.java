/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_java_json_tools.msg_simple;

import com.github.fge.msgsimple.bundle.MessageBundle;
import com.github.fge.msgsimple.load.MessageBundleLoader;
import com.github.fge.msgsimple.load.MessageBundles;
import com.github.fge.msgsimple.source.MapMessageSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MessageBundlesTest {
    @Test
    void getBundleInstantiatesPublicLoaderAndReturnsItsBundle() {
        MessageBundle bundle = MessageBundles.getBundle(TestMessageBundleLoader.class);

        assertThat(bundle.getMessage("greeting")).isEqualTo("Hello from loader");
        assertThat(bundle.getMessage("missing.key")).isEqualTo("missing.key");
    }

    @Test
    void getBundleCachesBundleForLoaderClass() {
        MessageBundle first = MessageBundles.getBundle(TestMessageBundleLoader.class);
        MessageBundle second = MessageBundles.getBundle(TestMessageBundleLoader.class);

        assertThat(second).isSameAs(first);
    }

    public static final class TestMessageBundleLoader implements MessageBundleLoader {
        public TestMessageBundleLoader() {
        }

        @Override
        public MessageBundle getBundle() {
            return MessageBundle.withSingleSource(MapMessageSource.newBuilder()
                .put("greeting", "Hello from loader")
                .build());
        }
    }
}
