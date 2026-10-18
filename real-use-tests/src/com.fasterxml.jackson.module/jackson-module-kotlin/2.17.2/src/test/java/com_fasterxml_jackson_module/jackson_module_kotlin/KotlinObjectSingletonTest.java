/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.module.kotlin.KotlinFeature;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import java.util.List;
import java.util.Map;
import kotlin.Unit;
import kotlin.coroutines.EmptyCoroutineContext;
import org.junit.jupiter.api.Test;

/// Kotlin `object` singletons bound to their canonical instance through the Kotlin module's
/// `SingletonSupport` feature (`KotlinObjectSingletonDeserializer`).
///
/// A Java workflow engine journals the outcome of every step that Kotlin workers run. A step
/// that returns nothing reports Kotlin's `Unit` object as its result, and a step that ran
/// outside any dispatcher reports `EmptyCoroutineContext` as its context. The engine replays
/// the journal after a restart and decides by identity whether a step produced a value, so the
/// replayed objects must be the very singletons the Kotlin runtime uses, not fresh copies.
class KotlinObjectSingletonTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new KotlinModule.Builder()
                    .enable(KotlinFeature.SingletonSupport)
                    .build())
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    /// One journal entry for a finished workflow step.
    public static final class StepOutcome {
        public String step;
        public Unit result;
        public EmptyCoroutineContext context;
        public int attempts;
    }

    private static boolean producedNoValue(StepOutcome outcome) {
        return outcome.result == Unit.INSTANCE;
    }

    @Test
    void replaysUnitResultsAsTheKotlinUnitObject() throws Exception {
        String journal = """
                [
                  {"step": "send-mail", "result": {}, "context": {}, "attempts": 1},
                  {"step": "archive", "result": {}, "context": {}, "attempts": 3},
                  {"step": "pending", "result": null, "context": null, "attempts": 0}
                ]
                """;

        List<StepOutcome> outcomes = MAPPER.readValue(journal, new TypeReference<List<StepOutcome>>() {
        });

        assertEquals(3, outcomes.size());
        StepOutcome sendMail = outcomes.get(0);
        StepOutcome archive = outcomes.get(1);
        StepOutcome pending = outcomes.get(2);

        assertSame(Unit.INSTANCE, sendMail.result);
        assertSame(Unit.INSTANCE, archive.result);
        assertSame(EmptyCoroutineContext.INSTANCE, sendMail.context);
        assertSame(EmptyCoroutineContext.INSTANCE, archive.context);
        assertEquals("kotlin.Unit", archive.result.toString());
        assertEquals("EmptyCoroutineContext", archive.context.toString());
        assertEquals(3, archive.attempts);

        assertNull(pending.result);
        assertNull(pending.context);
        assertEquals(List.of("send-mail", "archive"), outcomes.stream()
                .filter(KotlinObjectSingletonTest::producedNoValue)
                .map(outcome -> outcome.step)
                .toList());
    }

    @Test
    void journalsAStepAndReplaysTheSameSingletons() throws Exception {
        StepOutcome outcome = new StepOutcome();
        outcome.step = "rebuild-index";
        outcome.result = Unit.INSTANCE;
        outcome.context = EmptyCoroutineContext.INSTANCE;
        outcome.attempts = 2;

        String journaled = MAPPER.writeValueAsString(outcome);

        JsonNode tree = MAPPER.readTree(journaled);
        assertEquals("rebuild-index", tree.get("step").asText());
        assertEquals(0, tree.get("result").size());
        assertEquals(0, tree.get("context").size());
        assertEquals(2, tree.get("attempts").asInt());

        StepOutcome replayed = MAPPER.readValue(journaled, StepOutcome.class);
        assertSame(outcome.result, replayed.result);
        assertSame(outcome.context, replayed.context);
        assertEquals(outcome.attempts, replayed.attempts);
    }

    @Test
    void readsStandaloneObjectsAndMapValuesAsSingletons() throws Exception {
        assertSame(Unit.INSTANCE, MAPPER.readValue("{}", Unit.class));
        assertSame(EmptyCoroutineContext.INSTANCE, MAPPER.readValue("{}", EmptyCoroutineContext.class));

        Map<String, Unit> acknowledgements = MAPPER.readValue("""
                {"node-a": {}, "node-b": {}}
                """, new TypeReference<Map<String, Unit>>() {
        });

        assertEquals(List.of("node-a", "node-b"), List.copyOf(acknowledgements.keySet()));
        acknowledgements.values().forEach(ack -> assertSame(Unit.INSTANCE, ack));
        assertEquals("{\"node-a\":{},\"node-b\":{}}", MAPPER.writeValueAsString(acknowledgements));
    }
}
