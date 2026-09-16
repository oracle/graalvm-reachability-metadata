# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Receiver-obtainability eligibility for API-cover ranking
(§AR-code-coverage-improvement.4.1.2).

Builds the declared-type model from the extractor's `types.csv`/`methods.csv`
and computes which uncovered public entries a test can actually reach: the
least fixed point of the obtainability rules, then the per-entry route
(constructor, static, receiver, or supertype override).
"""

from __future__ import annotations

import csv
import os
from dataclasses import dataclass, field


class ApiRankError(RuntimeError):
    """Raised when the ranking inputs are missing or incoherent."""


@dataclass(frozen=True)
class PublicMethod:
    """One public method of a public class, split for eligibility analysis."""

    id: str
    name: str
    #: `name(params)`, without the return type, so a covariant override matches.
    signature: str
    return_type: str
    is_static: bool

    @property
    def is_constructor(self) -> bool:
        return self.name == "<init>"


@dataclass
class TypeModel:
    """Declared types and their public methods, keyed by owner.

    Owner is the join key in both directions: everything before `#` in a
    canonical method id is a `types.csv` name
    (§AR-code-coverage-improvement.4.1.2).
    """

    #: Direct supertypes (superclass plus interfaces) of every declared type.
    supertypes: dict[str, list[str]] = field(default_factory=dict)
    #: Public methods of public classes, grouped by declaring type.
    public_methods: dict[str, list[PublicMethod]] = field(default_factory=dict)
    #: Every type the extractor declared, so an unknown owner can fail open.
    declared_types: set[str] = field(default_factory=set)

    def all_supertypes(self, type_name: str) -> set[str]:
        """Transitive supertypes of `type_name`, excluding itself."""
        found: set[str] = set()
        pending: list[str] = list(self.supertypes.get(type_name, ()))
        while pending:
            current: str = pending.pop()
            if current not in found:
                found.add(current)
                pending.extend(self.supertypes.get(current, ()))
        return found


def split_method_id(method_id: str) -> tuple[str, str, str, str]:
    """`owner#name(params):ret` -> owner, name, `name(params)`, return type."""
    owner, member = method_id.split("#", 1)
    head, _, return_type = member.rpartition("):")
    return owner, member.split("(", 1)[0], f"{head})", return_type


def element_type(type_name: str) -> str:
    """`X[][]` -> `X`: returning an array hands the test an element."""
    while type_name.endswith("[]"):
        type_name = type_name[:-2]
    return type_name


def load_type_model(graph_dir: str) -> TypeModel | None:
    """Load `types.csv` plus the public methods of `methods.csv`.

    Returns `None` when the extractor predates `types.csv`, which switches
    eligibility filtering off rather than blocking every entry: a workspace
    whose graph was built by an older extractor must still rank.
    """
    types_path: str = os.path.join(graph_dir, "types.csv")
    methods_path: str = os.path.join(graph_dir, "methods.csv")
    if not os.path.isfile(types_path):
        return None

    model = TypeModel()
    try:
        with open(types_path, encoding="utf-8", newline="") as types_file:
            for row in csv.DictReader(types_file):
                parents: list[str] = [row["super"]] if row["super"] else []
                parents.extend(name for name in row["interfaces"].split(";") if name)
                model.supertypes[row["name"]] = parents
                model.declared_types.add(row["name"])
    except (OSError, csv.Error, KeyError) as error:
        raise ApiRankError(f"Cannot read '{types_path}': {error}") from error

    try:
        with open(methods_path, encoding="utf-8", newline="") as methods_file:
            reader = csv.DictReader(methods_file)
            if "isStatic" not in (reader.fieldnames or []):
                return None
            for row in reader:
                if row["isPublicApi"] != "true":
                    continue
                owner, name, signature, return_type = split_method_id(row["id"])
                model.public_methods.setdefault(owner, []).append(PublicMethod(
                    id=row["id"],
                    name=name,
                    signature=signature,
                    return_type=return_type,
                    is_static=row["isStatic"] == "true",
                ))
    except (OSError, csv.Error, KeyError) as error:
        raise ApiRankError(f"Cannot read '{methods_path}': {error}") from error
    return model


def obtainable_types(model: TypeModel) -> set[str]:
    """Least fixed point of the three obtainability rules (§3.1.2).

    Seeds are what a test can write from nothing: a public constructor, and the
    return type of any public static method, which needs no receiver. From
    there, holding a type yields the return types of its public methods. Every
    admission also admits the type's supertypes, because holding an instance
    permits every supertype method call on it.

    The frontier is a worklist rather than a repeated sweep: a type's public
    methods only ever need to be walked once, when it first becomes obtainable.
    """
    obtainable: set[str] = set()
    frontier: list[str] = []

    def admit(type_name: str) -> None:
        candidates: set[str] = {type_name} | model.all_supertypes(type_name)
        for candidate in candidates:
            if candidate in model.declared_types and candidate not in obtainable:
                obtainable.add(candidate)
                frontier.append(candidate)

    for owner, methods in model.public_methods.items():
        for method in methods:
            if method.is_constructor:
                admit(owner)
            elif method.is_static:
                admit(element_type(method.return_type))

    while frontier:
        for method in model.public_methods.get(frontier.pop(), ()):
            if not method.is_constructor:
                admit(element_type(method.return_type))
    return obtainable


def eligible_targets(model: TypeModel) -> dict[str, str]:
    """Map each eligible public method id to how a test reaches it (§3.1.2).

    Ineligible ids are simply absent. The override case is checked last and
    against the *public* signatures of obtainable supertypes only: a protected
    or package-private declaration upstream is not something a test can call.
    """
    obtainable: set[str] = obtainable_types(model)
    public_signatures: dict[str, set[str]] = {
        owner: {method.signature for method in methods}
        for owner, methods in model.public_methods.items()
    }

    eligible: dict[str, str] = {}
    for owner, methods in model.public_methods.items():
        supertypes: set[str] | None = None
        for method in methods:
            if method.is_constructor:
                eligible[method.id] = "constructor"
            elif method.is_static:
                eligible[method.id] = "static"
            elif owner in obtainable:
                eligible[method.id] = "receiver"
            else:
                if supertypes is None:
                    supertypes = {
                        name for name in model.all_supertypes(owner) if name in obtainable
                    }
                if any(method.signature in public_signatures.get(name, ())
                       for name in supertypes):
                    eligible[method.id] = "override"
    return eligible
