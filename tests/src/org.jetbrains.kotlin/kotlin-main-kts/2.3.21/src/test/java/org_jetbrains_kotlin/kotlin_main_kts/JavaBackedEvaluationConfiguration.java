/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_main_kts;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.script.experimental.api.ScriptEvaluationConfiguration;

public final class JavaBackedEvaluationConfiguration extends ScriptEvaluationConfiguration {
    public JavaBackedEvaluationConfiguration() {
        super((Function1<ScriptEvaluationConfiguration.Builder, Unit>) builder -> Unit.INSTANCE);
    }
}
