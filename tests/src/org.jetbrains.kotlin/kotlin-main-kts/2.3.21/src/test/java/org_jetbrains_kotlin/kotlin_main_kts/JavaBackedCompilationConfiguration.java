/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_main_kts;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.script.experimental.api.ScriptCompilationConfiguration;

public final class JavaBackedCompilationConfiguration extends ScriptCompilationConfiguration {
    public JavaBackedCompilationConfiguration() {
        super((Function1<ScriptCompilationConfiguration.Builder, Unit>) builder -> Unit.INSTANCE);
    }
}
