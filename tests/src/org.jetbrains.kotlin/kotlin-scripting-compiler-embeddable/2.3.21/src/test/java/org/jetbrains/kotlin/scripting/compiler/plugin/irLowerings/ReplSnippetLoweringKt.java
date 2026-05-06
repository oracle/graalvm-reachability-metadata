/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.jetbrains.kotlin.scripting.compiler.plugin.irLowerings;

import org.jetbrains.kotlin.name.Name;

/** Minimal fixture exposing the REPL eval function name used by K2ReplEvaluator. */
public final class ReplSnippetLoweringKt {
    private static final Name REPL_SNIPPET_EVAL_FUN_NAME = Name.identifier("$$eval");

    private ReplSnippetLoweringKt() {
    }

    public static Name getREPL_SNIPPET_EVAL_FUN_NAME() {
        return REPL_SNIPPET_EVAL_FUN_NAME;
    }
}
