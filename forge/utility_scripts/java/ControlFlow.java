/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
import java.lang.classfile.CodeElement;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.Opcode;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.classfile.instruction.BranchInstruction;
import java.lang.classfile.instruction.DiscontinuedInstruction;
import java.lang.classfile.instruction.ExceptionCatch;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LookupSwitchInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.SwitchCase;
import java.lang.classfile.instruction.TableSwitchInstruction;
import java.lang.classfile.instruction.ThrowInstruction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// Control-flow table of one method body, for deep-phase fork hints
/// (§AR-code-coverage-deep-navigation.1.3).
///
/// JaCoCo reports branches per source line and the PGO profile names successor
/// bcis; only the bytecode says where each branch leads. This table records every
/// conditional branch and switch with its distinct successor bcis, and the basic
/// blocks with their successors, exception edges included, so the analyzer can
/// tell which successor of a branch can still reach a given call.
///
/// Two encodings, both `;`-separated:
/// - branches: `bci[*]>succ[!],succ[!]` — `*` marks javac plumbing, `!` a
///   synthetic successor no test can reach;
/// - blocks: `start>succ,succ` — a block covers `[start, next block start)`.
final class ControlFlow {

    /// Exceptions javac throws from the hidden `default` of an exhaustive switch.
    private static final Set<String> SYNTHETIC_DEFAULT_EXCEPTIONS = Set.of(
            "java/lang/MatchException", "java/lang/IncompatibleClassChangeError");

    /// One instruction and the bci it starts at.
    private record Positioned(int bci, Instruction instruction) {
    }

    private final CodeAttribute code;
    private final List<Positioned> instructions = new ArrayList<>();
    private final NavigableMap<Integer, Instruction> byBci = new TreeMap<>();

    private ControlFlow(CodeAttribute code) {
        this.code = code;
        int bci = 0;
        for (CodeElement element : code.elementList()) {
            if (element instanceof Instruction instruction) {
                instructions.add(new Positioned(bci, instruction));
                byBci.put(bci, instruction);
                bci += instruction.sizeInBytes();
            }
        }
    }

    /// The `branches` and `blocks` fields of one method, or `null` when the
    /// method has no conditional branch and so can hold no fork.
    static String[] encode(CodeAttribute code) {
        ControlFlow flow = new ControlFlow(code);
        String branches = flow.branches();
        return branches.isEmpty() ? null : new String[]{branches, flow.blocks()};
    }

    private String branches() {
        Set<Integer> plumbing = plumbingBranches();
        List<String> entries = new ArrayList<>();
        for (Positioned positioned : instructions) {
            List<Integer> successors = decisionSuccessors(positioned);
            if (successors.isEmpty()) {
                continue;
            }
            Set<Integer> synthetic = syntheticSuccessors(positioned.instruction());
            String targets = successors.stream()
                    .map(successor -> successor + (synthetic.contains(successor) ? "!" : ""))
                    .collect(Collectors.joining(","));
            entries.add(positioned.bci() + (plumbing.contains(positioned.bci()) ? "*" : "") + ">" + targets);
        }
        return String.join(";", entries);
    }

    /// Distinct successors of a conditional branch or switch, ascending; empty
    /// for every other instruction, `goto` included, because it decides nothing.
    private List<Integer> decisionSuccessors(Positioned positioned) {
        Set<Integer> successors = new TreeSet<>();
        switch (positioned.instruction()) {
            case BranchInstruction branch when !isUnconditional(branch) -> {
                successors.add(bci(branch.target()));
                successors.add(positioned.bci() + branch.sizeInBytes());
            }
            case TableSwitchInstruction table -> {
                successors.add(bci(table.defaultTarget()));
                table.cases().forEach(switchCase -> successors.add(bci(switchCase.target())));
            }
            case LookupSwitchInstruction lookup -> {
                successors.add(bci(lookup.defaultTarget()));
                lookup.cases().forEach(switchCase -> successors.add(bci(switchCase.target())));
            }
            default -> {
            }
        }
        return new ArrayList<>(successors);
    }

    /// The hidden default of an exhaustive switch throws before doing anything
    /// else; JaCoCo drops it, and so does the hint.
    private Set<Integer> syntheticSuccessors(Instruction instruction) {
        Label defaultTarget = switch (instruction) {
            case TableSwitchInstruction table -> table.defaultTarget();
            case LookupSwitchInstruction lookup -> lookup.defaultTarget();
            default -> null;
        };
        if (defaultTarget == null) {
            return Set.of();
        }
        int target = bci(defaultTarget);
        return byBci.get(target) instanceof NewObjectInstruction allocation
                && SYNTHETIC_DEFAULT_EXCEPTIONS.contains(allocation.className().asInternalName())
                ? Set.of(target) : Set.of();
    }

    /// javac lowers a String switch to a switch on `hashCode()`, `equals` checks,
    /// and a switch on the resulting case index. Only the last one is the
    /// source-level decision, so the first two are marked the way JaCoCo
    /// filters them.
    private Set<Integer> plumbingBranches() {
        Set<Integer> plumbing = new TreeSet<>();
        for (int index = 1; index < instructions.size(); index++) {
            Positioned hashSwitch = instructions.get(index);
            if (!isSwitch(hashSwitch.instruction())
                    || !isStringHashCode(instructions.get(index - 1).instruction())) {
                continue;
            }
            plumbing.add(hashSwitch.bci());
            List<Integer> checks = new ArrayList<>();
            for (Positioned later : instructions.subList(index + 1, instructions.size())) {
                if (isSwitch(later.instruction())) {
                    // Only javac's shape ends in an index switch; without one the
                    // `equals` checks are the decisions themselves.
                    plumbing.addAll(checks);
                    break;
                }
                if (!decisionSuccessors(later).isEmpty()) {
                    checks.add(later.bci());
                }
            }
        }
        return plumbing;
    }

    /// Basic blocks with their successors: jump targets, the fall-through, then
    /// the handlers of every `try` covering the block, each written `bci~Type`
    /// as an exception edge with its caught type (`any` for a catch-all). A
    /// block holding a call or a `throw`, which can raise a caught exception, is
    /// written `bci!>` (§AR-code-coverage-deep-navigation.1.3).
    private String blocks() {
        Set<Integer> leaders = new TreeSet<>(List.of(0));
        for (Positioned positioned : instructions) {
            int next = positioned.bci() + positioned.instruction().sizeInBytes();
            List<Integer> targets = jumpTargets(positioned.instruction());
            leaders.addAll(targets);
            if (!targets.isEmpty() || endsFlow(positioned.instruction())) {
                leaders.add(next);
            }
        }
        for (ExceptionCatch handler : code.exceptionHandlers()) {
            leaders.add(bci(handler.tryStart()));
            leaders.add(bci(handler.tryEnd()));
            leaders.add(bci(handler.handler()));
        }
        leaders.removeIf(leader -> !byBci.containsKey(leader));

        List<Integer> starts = new ArrayList<>(leaders);
        List<String> entries = new ArrayList<>();
        for (int index = 0; index < starts.size(); index++) {
            int start = starts.get(index);
            Integer nextStart = index + 1 < starts.size() ? starts.get(index + 1) : null;
            int last = byBci.floorKey(nextStart == null ? Integer.MAX_VALUE : nextStart - 1);
            Instruction terminator = byBci.get(last);
            Set<Integer> successors = new LinkedHashSet<>(jumpTargets(terminator));
            if (!endsFlow(terminator) && !isUnconditional(terminator) && nextStart != null) {
                successors.add(nextStart);
            }
            Map<Integer, String> handlers = new LinkedHashMap<>();
            for (ExceptionCatch handler : code.exceptionHandlers()) {
                int handlerBci = bci(handler.handler());
                if (bci(handler.tryStart()) <= start && start < bci(handler.tryEnd())
                        && !successors.contains(handlerBci)) {
                    handlers.putIfAbsent(handlerBci, handler.catchType()
                            .map(type -> type.asInternalName().replace('/', '.'))
                            .orElse("any"));
                }
            }
            boolean canThrow = byBci.subMap(start, last + 1).values().stream()
                    .anyMatch(ControlFlow::canRaise);
            entries.add(start + (canThrow ? "!" : "") + ">" + Stream.concat(
                    successors.stream().map(String::valueOf),
                    handlers.entrySet().stream().map(entry -> entry.getKey() + "~" + entry.getValue()))
                    .collect(Collectors.joining(",")));
        }
        return String.join(";", entries);
    }

    /// Every bci an instruction may jump to, its fall-through excluded.
    private List<Integer> jumpTargets(Instruction instruction) {
        List<Integer> targets = new ArrayList<>();
        switch (instruction) {
            case BranchInstruction branch -> targets.add(bci(branch.target()));
            case DiscontinuedInstruction.JsrInstruction jsr -> targets.add(bci(jsr.target()));
            case TableSwitchInstruction table -> {
                targets.add(bci(table.defaultTarget()));
                table.cases().stream().map(SwitchCase::target).forEach(label -> targets.add(bci(label)));
            }
            case LookupSwitchInstruction lookup -> {
                targets.add(bci(lookup.defaultTarget()));
                lookup.cases().stream().map(SwitchCase::target).forEach(label -> targets.add(bci(label)));
            }
            default -> {
            }
        }
        return targets;
    }

    /// Whether the instruction can raise an exception a `catch` is written for:
    /// a call or a `throw`. Implicit exceptions of other instructions are not
    /// modelled.
    private static boolean canRaise(Instruction instruction) {
        return instruction instanceof InvokeInstruction || instruction instanceof ThrowInstruction;
    }

    /// Whether control never falls through to the next instruction.
    private static boolean endsFlow(Instruction instruction) {
        return instruction instanceof ReturnInstruction
                || instruction instanceof ThrowInstruction
                || instruction instanceof DiscontinuedInstruction.RetInstruction
                || isSwitch(instruction);
    }

    private static boolean isUnconditional(Instruction instruction) {
        return instruction instanceof BranchInstruction branch
                && (branch.opcode() == Opcode.GOTO || branch.opcode() == Opcode.GOTO_W);
    }

    private static boolean isSwitch(Instruction instruction) {
        return instruction instanceof TableSwitchInstruction || instruction instanceof LookupSwitchInstruction;
    }

    private static boolean isStringHashCode(Instruction instruction) {
        return instruction instanceof InvokeInstruction invoke
                && invoke.opcode() == Opcode.INVOKEVIRTUAL
                && "java/lang/String".equals(invoke.owner().asInternalName())
                && "hashCode".equals(invoke.name().stringValue())
                && "()I".equals(invoke.type().stringValue());
    }

    private int bci(Label label) {
        return code.labelToBci(label);
    }
}
