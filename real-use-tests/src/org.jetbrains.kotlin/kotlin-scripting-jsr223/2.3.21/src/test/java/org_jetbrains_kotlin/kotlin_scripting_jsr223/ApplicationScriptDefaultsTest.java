/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jsr223;

import static org.assertj.core.api.Assertions.assertThat;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import kotlin.script.experimental.jsr223.KotlinJsr223DefaultScriptEngineFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * JSR-223 global-scope configuration supplies application defaults to Kotlin job contexts.
 *
 * A billing host prepares jobs with shared currency and approval settings. A special job overrides
 * its approval limit without changing the application default or another job, then removes the
 * override to resume using the default. Configuration is resolved before executing any script.
 */
@Timeout(60)
public class ApplicationScriptDefaultsTest {

    @Test
    void resolvesJobOverridesBeforeSharedBillingDefaults() {
        ScriptEngineManager manager = new ScriptEngineManager();
        manager.registerEngineName("billing-kotlin", new KotlinJsr223DefaultScriptEngineFactory());
        manager.put("currency", "EUR");
        manager.put("approvalLimitInCents", 50000);

        ScriptEngine standardJob = manager.getEngineByName("billing-kotlin");
        ScriptEngine specialJob = manager.getEngineByName("billing-kotlin");
        assertThat(standardJob).isNotNull();
        assertThat(specialJob).isNotNull();
        ScriptContext standardContext = standardJob.getContext();
        ScriptContext specialContext = specialJob.getContext();

        assertThat(standardContext.getAttribute("currency")).isEqualTo("EUR");
        assertThat(specialContext.getAttribute("currency")).isEqualTo("EUR");
        assertThat(standardContext.getAttribute("approvalLimitInCents")).isEqualTo(50000);
        assertThat(specialContext.getAttributesScope("approvalLimitInCents"))
                .isEqualTo(ScriptContext.GLOBAL_SCOPE);

        specialJob.put("approvalLimitInCents", 100000);

        assertThat(specialContext.getAttribute("approvalLimitInCents")).isEqualTo(100000);
        assertThat(specialContext.getAttributesScope("approvalLimitInCents"))
                .isEqualTo(ScriptContext.ENGINE_SCOPE);
        assertThat(standardContext.getAttribute("approvalLimitInCents")).isEqualTo(50000);
        assertThat(manager.get("approvalLimitInCents")).isEqualTo(50000);

        manager.put("approvalLimitInCents", 60000);

        assertThat(standardContext.getAttribute("approvalLimitInCents")).isEqualTo(60000);
        assertThat(specialContext.getAttribute("approvalLimitInCents")).isEqualTo(100000);

        Bindings overrides = specialJob.getBindings(ScriptContext.ENGINE_SCOPE);
        overrides.remove("approvalLimitInCents");

        assertThat(specialContext.getAttribute("approvalLimitInCents")).isEqualTo(60000);
        assertThat(specialContext.getAttributesScope("approvalLimitInCents"))
                .isEqualTo(ScriptContext.GLOBAL_SCOPE);
        assertThat(specialContext.getAttribute("currency")).isEqualTo("EUR");
    }
}
