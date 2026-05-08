/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package software_amazon_awssdk.sdk_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.core.internal.http.loader.DefaultSdkHttpClientBuilder;
import software.amazon.awssdk.http.ExecutableHttpRequest;
import software.amazon.awssdk.http.HttpExecuteRequest;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpService;
import software.amazon.awssdk.utils.AttributeMap;

public class SystemPropertyHttpServiceProviderTest {
    @Test
    void defaultSyncClientBuilderInstantiatesClassNamedBySystemProperty() {
        String property = SdkSystemSetting.SYNC_HTTP_SERVICE_IMPL.property();
        String previousValue = System.getProperty(property);
        System.setProperty(property, TestSdkHttpService.class.getName());
        try (SdkHttpClient client = new DefaultSdkHttpClientBuilder().buildWithDefaults(AttributeMap.empty())) {
            assertThat(client).isInstanceOf(TestSdkHttpClient.class);
        } finally {
            restoreProperty(property, previousValue);
        }
    }

    public static final class TestSdkHttpService implements SdkHttpService {
        @Override
        public SdkHttpClient.Builder<?> createHttpClientBuilder() {
            return new TestSdkHttpClientBuilder();
        }
    }

    public static final class TestSdkHttpClientBuilder implements SdkHttpClient.Builder<TestSdkHttpClientBuilder> {
        @Override
        public SdkHttpClient buildWithDefaults(AttributeMap serviceDefaults) {
            return new TestSdkHttpClient();
        }
    }

    public static final class TestSdkHttpClient implements SdkHttpClient {
        @Override
        public ExecutableHttpRequest prepareRequest(HttpExecuteRequest request) {
            throw new UnsupportedOperationException("Requests are not executed by this test client");
        }

        @Override
        public void close() {
        }
    }

    private static void restoreProperty(String property, String previousValue) {
        if (previousValue == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, previousValue);
        }
    }
}
