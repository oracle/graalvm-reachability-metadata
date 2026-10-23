/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jsr223;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import kotlin.script.experimental.jsr223.KotlinJsr223DefaultScriptEngineFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * JSR-223 engine bindings isolate request data in prepared Kotlin scripting sessions.
 *
 * A rule-processing service prepares separate sessions for incoming orders before handing them to
 * its execution stage. Replacing a session's input must not expose a previous customer's order or
 * change another session. Preparing inputs does not compile or execute a script.
 */
@Timeout(60)
public class RequestScriptSessionTest {

    @Test
    void preparesIndependentOrderInputsAndReplacesACompletedRequest() {
        ScriptEngineFactory factory = new KotlinJsr223DefaultScriptEngineFactory();
        ScriptEngine firstSession = factory.getScriptEngine();
        ScriptEngine secondSession = factory.getScriptEngine();
        Order firstOrder = new Order("order-104", "north-shop", List.of(1200, 800));
        Order secondOrder = new Order("order-105", "south-shop", List.of(4500));

        prepare(firstSession, firstOrder);
        prepare(secondSession, secondOrder);
        firstSession.put("discountPercent", 10);
        secondSession.put("discountPercent", 5);

        assertThat(firstSession.get("order")).isSameAs(firstOrder);
        assertThat(secondSession.get("order")).isSameAs(secondOrder);
        assertThat(firstSession.get("discountPercent")).isEqualTo(10);
        assertThat(secondSession.get("discountPercent")).isEqualTo(5);

        firstSession.put("approvalNote", "Approved by the north-shop operator");
        assertThat(secondSession.get("approvalNote")).isNull();

        Order nextOrder = new Order("order-106", "west-shop", List.of(700, 300));
        prepare(firstSession, nextOrder);

        Bindings prepared = firstSession.getBindings(ScriptContext.ENGINE_SCOPE);
        assertThat(prepared).containsEntry("order", nextOrder)
                .doesNotContainKeys("approvalNote", "discountPercent");
        assertThat(firstSession.get("order")).isSameAs(nextOrder);
        assertThat(secondSession.get("order")).isSameAs(secondOrder);
        assertThat(secondSession.get("discountPercent")).isEqualTo(5);
    }

    private static void prepare(ScriptEngine session, Order order) {
        Bindings inputs = session.createBindings();
        inputs.put("order", order);
        session.setBindings(inputs, ScriptContext.ENGINE_SCOPE);
    }

    public record Order(String id, String customer, List<Integer> lineAmountsInCents) {
    }
}
