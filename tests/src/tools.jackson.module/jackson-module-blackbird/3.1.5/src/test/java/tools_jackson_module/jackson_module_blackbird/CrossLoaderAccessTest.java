/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_module.jackson_module_blackbird;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.blackbird.BlackbirdModule;

public class CrossLoaderAccessTest {
    private static final int CONCURRENT_TYPE_COUNT = 16;
    private static final MethodHandles.Lookup PACKAGE_LOOKUP = MethodHandles.lookup()
            .dropLookupMode(MethodHandles.Lookup.PRIVATE);
    private static final List<String> CONCURRENT_JSON;

    static final ObjectMapper MAPPER;

    static {
        PackageLookupSupplier lookupSupplier = new PackageLookupSupplier(CONCURRENT_TYPE_COUNT);
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new PackageLookupBlackbirdModule(lookupSupplier))
                .build();
        CONCURRENT_JSON = serializeConcurrently(mapper);
        MAPPER = mapper;
    }

    @Test
    void grantsPackageAccessDuringConcurrentSerializerCreation() {
        assertThat(CONCURRENT_JSON).hasSize(CONCURRENT_TYPE_COUNT);
        for (int index = 0; index < CONCURRENT_TYPE_COUNT; index++) {
            assertThat(CONCURRENT_JSON.get(index))
                    .isEqualTo("{\"text\":\"message-" + (index + 1) + "\"}");
        }
    }

    @Test
    void roundTripsInheritedPropertiesWithPackageLookup() throws Exception {
        InheritedMessage message = new InheritedMessage();
        message.setText("inherited-accessor");

        String json = MAPPER.writeValueAsString(message);
        InheritedMessage restored = MAPPER.readValue(json, InheritedMessage.class);

        assertThat(MAPPER.readTree(json).get("text").asText()).isEqualTo("inherited-accessor");
        assertThat(restored.getText()).isEqualTo("inherited-accessor");
    }

    private static List<String> serializeConcurrently(ObjectMapper mapper) {
        List<Callable<String>> tasks = new ArrayList<>();
        for (Object message : concurrentMessages()) {
            tasks.add(new SerializationTask(mapper, message));
        }

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_TYPE_COUNT);
        try {
            List<Future<String>> futures = executor.invokeAll(tasks, 10, TimeUnit.SECONDS);
            List<String> json = new ArrayList<>(futures.size());
            for (Future<String> future : futures) {
                if (future.isCancelled()) {
                    throw new IllegalStateException("Concurrent serializer creation did not complete");
                }
                json.add(future.get());
            }
            return json;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExceptionInInitializerError(e);
        } catch (ExecutionException e) {
            throw new ExceptionInInitializerError(e.getCause());
        } finally {
            executor.shutdownNow();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent serializer executor did not terminate");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ExceptionInInitializerError(e);
            }
        }
    }

    private static List<Object> concurrentMessages() {
        return List.of(
                new ConcurrentMessage1("message-1"),
                new ConcurrentMessage2("message-2"),
                new ConcurrentMessage3("message-3"),
                new ConcurrentMessage4("message-4"),
                new ConcurrentMessage5("message-5"),
                new ConcurrentMessage6("message-6"),
                new ConcurrentMessage7("message-7"),
                new ConcurrentMessage8("message-8"),
                new ConcurrentMessage9("message-9"),
                new ConcurrentMessage10("message-10"),
                new ConcurrentMessage11("message-11"),
                new ConcurrentMessage12("message-12"),
                new ConcurrentMessage13("message-13"),
                new ConcurrentMessage14("message-14"),
                new ConcurrentMessage15("message-15"),
                new ConcurrentMessage16("message-16"));
    }

    public static final class PackageLookupBlackbirdModule extends BlackbirdModule {
        private final Supplier<MethodHandles.Lookup> lookupSupplier;

        PackageLookupBlackbirdModule(Supplier<MethodHandles.Lookup> lookupSupplier) {
            this.lookupSupplier = lookupSupplier;
        }

        @Override
        protected Supplier<MethodHandles.Lookup> findLookupSupplier() {
            return lookupSupplier;
        }
    }

    private static final class PackageLookupSupplier implements Supplier<MethodHandles.Lookup> {
        private final CountDownLatch participants;
        private final CountDownLatch release = new CountDownLatch(1);

        PackageLookupSupplier(int participantCount) {
            participants = new CountDownLatch(participantCount);
        }

        @Override
        public MethodHandles.Lookup get() {
            if (participants.getCount() == 0) {
                return PACKAGE_LOOKUP;
            }
            participants.countDown();
            if (participants.getCount() == 0) {
                release.countDown();
            }
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Concurrent lookup requests did not arrive");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while coordinating lookup requests", e);
            }
            return PACKAGE_LOOKUP;
        }
    }

    private static final class SerializationTask implements Callable<String> {
        private final ObjectMapper mapper;
        private final Object message;

        SerializationTask(ObjectMapper mapper, Object message) {
            this.mapper = mapper;
            this.message = message;
        }

        @Override
        public String call() throws Exception {
            return mapper.writeValueAsString(message);
        }
    }

    public static class MessageBase {
        private String text;

        public MessageBase() {
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    public static final class InheritedMessage extends MessageBase {
        public InheritedMessage() {
        }
    }

    public record ConcurrentMessage1(String text) {
    }

    public record ConcurrentMessage2(String text) {
    }

    public record ConcurrentMessage3(String text) {
    }

    public record ConcurrentMessage4(String text) {
    }

    public record ConcurrentMessage5(String text) {
    }

    public record ConcurrentMessage6(String text) {
    }

    public record ConcurrentMessage7(String text) {
    }

    public record ConcurrentMessage8(String text) {
    }

    public record ConcurrentMessage9(String text) {
    }

    public record ConcurrentMessage10(String text) {
    }

    public record ConcurrentMessage11(String text) {
    }

    public record ConcurrentMessage12(String text) {
    }

    public record ConcurrentMessage13(String text) {
    }

    public record ConcurrentMessage14(String text) {
    }

    public record ConcurrentMessage15(String text) {
    }

    public record ConcurrentMessage16(String text) {
    }
}
