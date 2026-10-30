/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_scripting_jvm_host;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.coroutines.intrinsics.IntrinsicsKt;
import kotlin.script.experimental.api.EvaluationResult;
import kotlin.script.experimental.api.ResultWithDiagnostics;
import kotlin.script.experimental.api.ScriptCompilationConfiguration;
import kotlin.script.experimental.api.ScriptEvaluationConfiguration;
import kotlin.script.experimental.api.ScriptEvaluator;
import kotlin.script.experimental.jvm.impl.KJvmCompiledScript;
import org.jetbrains.kotlin.scripting.compiler.plugin.impl.KJvmCompiledModuleInMemoryImpl;

final class ScriptFixtures {
    static final String CLASS_NAME = "org_jetbrains_kotlin.kotlin_scripting_jvm_host.ScriptFixtures";
    static final String CLASS_ENTRY = CLASS_NAME.replace('.', '/') + ".class";
    static final String RESOURCE_ENTRY = "assets/greeting.txt";
    static final byte[] RESOURCE_BYTES = "Hello from an archived script".getBytes(StandardCharsets.UTF_8);

    private ScriptFixtures() {
    }

    static ScriptCompilationConfiguration compilationConfiguration() {
        return new ScriptCompilationConfiguration(builder -> Unit.INSTANCE);
    }

    static ScriptEvaluationConfiguration evaluationConfiguration() {
        return new ScriptEvaluationConfiguration(builder -> Unit.INSTANCE);
    }

    static byte[] classBytes() throws IOException {
        try (InputStream input = ScriptFixtures.class.getResourceAsStream("/" + CLASS_ENTRY)) {
            if (input == null) {
                throw new IOException("Missing compiled fixture resource: " + CLASS_ENTRY);
            }
            return input.readAllBytes();
        }
    }

    static KJvmCompiledScript compiledScript(ScriptCompilationConfiguration configuration) throws IOException {
        Map<String, byte[]> outputs = Map.of(CLASS_ENTRY, classBytes(), RESOURCE_ENTRY, RESOURCE_BYTES);
        return new KJvmCompiledScript("greeting.kts", configuration, CLASS_NAME, null, List.of(),
                new KJvmCompiledModuleInMemoryImpl(outputs));
    }

    static Object evaluate(ScriptEvaluator evaluator, KJvmCompiledScript script,
                           ScriptEvaluationConfiguration configuration) throws Exception {
        CompletableFuture<Object> completion = new CompletableFuture<>();
        Continuation<ResultWithDiagnostics<EvaluationResult>> continuation = new Continuation<>() {
            @Override
            public CoroutineContext getContext() {
                return EmptyCoroutineContext.INSTANCE;
            }

            @Override
            public void resumeWith(Object result) {
                completion.complete(result);
            }
        };
        Object result = evaluator.invoke(script, configuration, continuation);
        return result == IntrinsicsKt.getCOROUTINE_SUSPENDED() ? completion.get(30, TimeUnit.SECONDS) : result;
    }
}
