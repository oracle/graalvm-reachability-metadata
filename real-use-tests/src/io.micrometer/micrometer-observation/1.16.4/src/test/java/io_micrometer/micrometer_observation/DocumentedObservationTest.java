/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_observation;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.GlobalObservationConvention;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.ObservationTextPublisher;
import io.micrometer.observation.docs.ObservationDocumentation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Documents observations with ObservationDocumentation and KeyName, resolving their names and
 * key values through default, custom, and global conventions and publishing them as text.
 *
 * A payment service declares its observations as an enum, the way instrumented libraries do,
 * with documented low and high cardinality key names, a documented event, and a default
 * convention. The test starts observations from that documentation and asserts on the names,
 * key values, and lifecycle lines the registry's handlers observe, including what happens when
 * an application registers a global convention or passes its own.
 */
class DocumentedObservationTest {

    private List<String> published;

    private List<Observation.Context> stopped;

    private ObservationRegistry registry;

    @BeforeEach
    void setUp() {
        published = new CopyOnWriteArrayList<>();
        stopped = new CopyOnWriteArrayList<>();
        registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new ObservationTextPublisher(published::add,
                        context -> context instanceof PaymentContext))
                .observationHandler(new StoppedContextCollector(stopped));
    }

    @Test
    void documentedObservationUsesDeclaredNameAndKeyNames() {
        Observation observation = PaymentObservation.AUTHORIZE.observation(registry, PaymentContext::new)
                .lowCardinalityKeyValue(PaymentObservation.LowKeys.METHOD.withValue("card"))
                .highCardinalityKeyValue(PaymentObservation.HighKeys.PAYMENT_ID.withValue("pay-1"));

        String result = observation.observe(() -> {
            observation.event(PaymentObservation.Events.FRAUD_CHECKED);
            return "authorized";
        });

        assertThat(result).isEqualTo("authorized");
        assertThat(stopped).hasSize(1);
        Observation.Context context = stopped.get(0);
        assertThat(context.getName()).isEqualTo("payment.authorize");
        assertThat(context.getContextualName()).isEqualTo("authorize payment");
        assertThat(context.getLowCardinalityKeyValue("payment.method").getValue()).isEqualTo("card");
        assertThat(context.getHighCardinalityKeyValue("payment.id").getValue()).isEqualTo("pay-1");
        assertThat(published).extracting(line -> line.substring(0, line.indexOf(" - ")).trim())
                .containsExactly("START", "OPEN", "EVENT", "CLOSE", "STOP");
        assertThat(published.get(0)).contains("name='payment.authorize'");
        assertThat(published.get(2)).contains("FRAUD_CHECKED");
        assertThat(published.get(4)).contains("payment.id='pay-1'");
    }

    @Test
    void keyNamesDescribeTheirDocumentedValues() {
        KeyName[] all = KeyName.merge(PaymentObservation.CHARGE.getLowCardinalityKeyNames(),
                PaymentObservation.CHARGE.getHighCardinalityKeyNames());

        assertThat(all).extracting(KeyName::asString)
                .containsExactly("payment.method", "payment.outcome", "payment.id");
        assertThat(PaymentObservation.LowKeys.OUTCOME.isRequired()).isTrue();
        assertThat(PaymentObservation.LowKeys.METHOD.withNoneValue().getValue()).isEqualTo(KeyValue.NONE_VALUE);
        KeyValue validated = PaymentObservation.HighKeys.PAYMENT_ID.withValue("pay-9",
                value -> value.toString().startsWith("pay"));
        assertThat(validated.getKey()).isEqualTo("payment.id");
        assertThat(validated.getValue()).isEqualTo("pay-9");
        assertThatThrownBy(() -> PaymentObservation.HighKeys.PAYMENT_ID.withValue("bogus",
                value -> value.toString().startsWith("pay")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(PaymentObservation.CHARGE.getEvents()).containsExactly(PaymentObservation.Events.FRAUD_CHECKED);
        assertThat(PaymentObservation.AUTHORIZE.getEvents()).isSameAs(ObservationDocumentation.EMPTY_EVENT_NAMES);
        assertThat(PaymentObservation.AUTHORIZE.getHighCardinalityKeyNames()).isSameAs(ObservationDocumentation.EMPTY);
    }

    @Test
    void defaultConventionNamesConventionBasedObservation() {
        PaymentContext context = new PaymentContext();
        context.amountCents = 2500;
        context.approved = true;

        PaymentObservation.CHARGE.observation(null, new DefaultChargeConvention(), () -> context, registry)
                .observe(() -> {
                });

        assertThat(stopped).containsExactly(context);
        assertThat(context.getName()).isEqualTo("payment.charge");
        assertThat(context.getContextualName()).isEqualTo("charge card");
        assertThat(context.getLowCardinalityKeyValues()).containsExactlyInAnyOrder(
                KeyValue.of("payment.method", "card"), KeyValue.of("payment.outcome", "approved"));
        assertThat(context.getHighCardinalityKeyValue("payment.id").getValue()).isEqualTo("charge-2500");
    }

    @Test
    void globalConventionOverridesDefaultButNotCustomConvention() {
        registry.observationConfig().observationConvention(new TenantChargeConvention("acme"));

        PaymentContext global = new PaymentContext();
        global.approved = false;
        PaymentObservation.CHARGE.start(null, new DefaultChargeConvention(), () -> global, registry).stop();

        PaymentContext custom = new PaymentContext();
        custom.approved = true;
        PaymentObservation.CHARGE.start(new LegacyChargeConvention(), new DefaultChargeConvention(), () -> custom,
                registry).stop();

        assertThat(stopped).containsExactly(global, custom);
        assertThat(global.getContextualName()).isEqualTo("acme charge");
        assertThat(global.getLowCardinalityKeyValues()).containsExactlyInAnyOrder(
                KeyValue.of("tenant", "acme"), KeyValue.of("payment.outcome", "declined"));
        assertThat(custom.getContextualName()).isEqualTo("legacy charge");
        assertThat(custom.getLowCardinalityKeyValue("payment.outcome").getValue()).isEqualTo("approved");
        assertThat(custom.getLowCardinalityKeyValue("tenant")).isNull();
    }

    @Test
    void documentationRejectsMissingOrForeignDefaultConvention() {
        assertThatThrownBy(() -> PaymentObservation.AUTHORIZE.observation(null, new DefaultChargeConvention(),
                PaymentContext::new, registry))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> PaymentObservation.CHARGE.observation(null, new TenantChargeConvention("acme"),
                PaymentContext::new, registry))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(stopped).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void textPublisherSkipsContextsItDoesNotSupport() {
        Observation.createNotStarted("inventory.reserve", registry).observe(() -> {
        });

        assertThat(stopped).extracting(Observation.Context::getName).containsExactly("inventory.reserve");
        assertThat(published).isEmpty();
    }

    /** The payment service's documented observations. */
    enum PaymentObservation implements ObservationDocumentation {

        AUTHORIZE {
            @Override
            public String getName() {
                return "payment.authorize";
            }

            @Override
            public String getContextualName() {
                return "authorize payment";
            }

            @Override
            public KeyName[] getLowCardinalityKeyNames() {
                return new KeyName[] {LowKeys.METHOD};
            }
        },

        CHARGE {
            @Override
            public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
                return DefaultChargeConvention.class;
            }

            @Override
            public KeyName[] getLowCardinalityKeyNames() {
                return LowKeys.values();
            }

            @Override
            public KeyName[] getHighCardinalityKeyNames() {
                return HighKeys.values();
            }

            @Override
            public Observation.Event[] getEvents() {
                return Events.values();
            }
        };

        /** Low cardinality key names. */
        enum LowKeys implements KeyName {

            METHOD {
                @Override
                public String asString() {
                    return "payment.method";
                }

                @Override
                public boolean isRequired() {
                    return false;
                }
            },

            OUTCOME {
                @Override
                public String asString() {
                    return "payment.outcome";
                }
            }
        }

        /** High cardinality key names. */
        enum HighKeys implements KeyName {

            PAYMENT_ID {
                @Override
                public String asString() {
                    return "payment.id";
                }
            }
        }

        /** Documented events. */
        enum Events implements Observation.Event {

            FRAUD_CHECKED {
                @Override
                public String getName() {
                    return "fraud.checked";
                }
            }
        }
    }

    /** Context carrying the charge an application is processing. */
    static final class PaymentContext extends Observation.Context {

        private long amountCents;

        private boolean approved;
    }

    /** Convention the payment service ships as default. */
    static class DefaultChargeConvention implements ObservationConvention<PaymentContext> {

        @Override
        public String getName() {
            return "payment.charge";
        }

        @Override
        public String getContextualName(PaymentContext context) {
            return "charge card";
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(PaymentContext context) {
            return KeyValues.of(PaymentObservation.LowKeys.METHOD.withValue("card"), outcome(context));
        }

        @Override
        public KeyValues getHighCardinalityKeyValues(PaymentContext context) {
            return KeyValues.of(PaymentObservation.HighKeys.PAYMENT_ID.withValue("charge-" + context.amountCents));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof PaymentContext;
        }

        static KeyValue outcome(PaymentContext context) {
            return PaymentObservation.LowKeys.OUTCOME.withValue(context.approved ? "approved" : "declined");
        }
    }

    /** Convention an application passes explicitly for one call site. */
    static final class LegacyChargeConvention extends DefaultChargeConvention {

        @Override
        public String getContextualName(PaymentContext context) {
            return "legacy charge";
        }
    }

    /** Convention an application registers globally to tag every charge with its tenant. */
    static final class TenantChargeConvention implements GlobalObservationConvention<PaymentContext> {

        private final String tenant;

        TenantChargeConvention(String tenant) {
            this.tenant = tenant;
        }

        @Override
        public String getName() {
            return "payment.charge";
        }

        @Override
        public String getContextualName(PaymentContext context) {
            return tenant + " charge";
        }

        @Override
        public KeyValues getLowCardinalityKeyValues(PaymentContext context) {
            return KeyValues.of(KeyValue.of("tenant", tenant), DefaultChargeConvention.outcome(context));
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof PaymentContext;
        }
    }

    /** Handler that keeps every stopped context for assertions. */
    static final class StoppedContextCollector implements ObservationHandler<Observation.Context> {

        private final List<Observation.Context> stopped;

        StoppedContextCollector(List<Observation.Context> stopped) {
            this.stopped = stopped;
        }

        @Override
        public void onStop(Observation.Context context) {
            stopped.add(context);
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }
    }
}
