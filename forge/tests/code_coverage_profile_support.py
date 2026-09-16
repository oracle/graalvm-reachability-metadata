# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Shared fixtures and helpers for the deep-method profile-report tests."""

import json
import os

from utility_scripts.code_coverage_jacoco import (
    JacocoMethodCoverage,
    load_jacoco_method_coverage,
)
from utility_scripts.code_coverage_model import MethodRef

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures", "code_coverage")
JACOCO_PATH = os.path.join(FIXTURES, "near_call_jacoco.xml")

INIT_ID = "com.example.Registry#init():void"
RESOLVE_ID = "com.example.Registry#resolve(java.lang.String):void"
RESOLVE_INTEGER_ID = "com.example.Registry#resolve(java.lang.Integer):void"
LOAD_ID = "com.example.Registry#load(java.lang.String):com.example.Driver"
RELOAD_ID = "com.example.Registry#reload():void"
ORPHAN_ID = "com.example.Registry#orphan(int):void"
OF_ID = "com.example.parser.Config#of():com.example.parser.Config"
PARSE_ID = "com.example.parser.Config#parse(java.lang.String):com.example.parser.Config"
JACOCO_ONLY_ID = "com.example.diagnostic.JacocoOnly#ghost():void"


def _load_inventory() -> dict:
    with open(os.path.join(FIXTURES, "near_call_inventory.json"), encoding="utf-8") as inventory_file:
        return json.load(inventory_file)


def _load_jacoco() -> dict[str, JacocoMethodCoverage]:
    return load_jacoco_method_coverage([JACOCO_PATH])


def _coverage(ref: MethodRef, covered: bool = False) -> JacocoMethodCoverage:
    return JacocoMethodCoverage(
        method_ref=ref,
        covered=covered,
        source_path=f"{ref.owner.replace('.', '/')}.java",
        source_line=1,
        report_paths=("fixture.xml",),
    )
