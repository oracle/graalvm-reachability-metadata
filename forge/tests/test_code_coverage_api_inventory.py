# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import shutil
import subprocess
import tempfile
import unittest

from utility_scripts import code_coverage_api_inventory as inventory_module

FIXTURES = os.path.join(os.path.dirname(__file__), "fixtures", "code_coverage")


def _java_tool(name: str) -> str | None:
    home = os.environ.get("GRAALVM_HOME") or os.environ.get("JAVA_HOME")
    if home:
        candidate = os.path.join(home, "bin", name)
        if os.path.isfile(candidate):
            return candidate
    return shutil.which(name)


_GREETER_SRC = """package com.example;

public class Greeter {
    private final String who;
    public Greeter(String who) { this.who = who; }
    public String greet() { return "hello " + who; }
    public String shout() { return greet().toUpperCase(); }
    public static Greeter of(String who) { return new Greeter(who); }
    private String secret() { return who; }
}
"""

JAVAP_SAMPLE = '''Compiled from "Greeter.java"
public class com.example.Greeter {
  public com.example.Greeter(java.lang.String);
  public java.lang.String greet();
  public static com.example.Greeter of(java.lang.String);
}
Compiled from "Mode.java"
public final class com.example.Mode extends java.lang.Enum<com.example.Mode> {
  public static final com.example.Mode QUIET;
  public static com.example.Mode valueOf(java.lang.String);
}
'''


class JavapParsingTests(unittest.TestCase):
    def test_parses_constructor_method_and_static(self) -> None:
        classes = inventory_module.parse_javap(JAVAP_SAMPLE, source_root="src/main/java")
        greeter = next(c for c in classes if c.owner == "com.example.Greeter")
        ids = {t.target_id: t for t in greeter.targets}
        self.assertIn("com.example.Greeter#<init>(java.lang.String):void", ids)
        self.assertEqual(ids["com.example.Greeter#<init>(java.lang.String):void"].kind, "constructor")
        self.assertIn("com.example.Greeter#greet():java.lang.String", ids)
        of_target = ids["com.example.Greeter#of(java.lang.String):com.example.Greeter"]
        self.assertEqual(of_target.kind, "staticMethod")
        self.assertEqual(greeter.source_path, "src/main/java/com/example/Greeter.java")

    def test_enum_constant_and_generics_erasure(self) -> None:
        classes = inventory_module.parse_javap(JAVAP_SAMPLE)
        mode = next(c for c in classes if c.owner == "com.example.Mode")
        kinds = {t.target_id: t.kind for t in mode.targets}
        self.assertEqual(kinds.get("com.example.Mode#QUIET"), "enumConstant")
        self.assertIn("com.example.Mode#valueOf(java.lang.String):com.example.Mode", kinds)

    def test_varargs_normalized_to_array(self) -> None:
        text = (
            'Compiled from "F.java"\n'
            "public class com.example.F {\n"
            "  public void log(java.lang.String...);\n"
            "}\n"
        )
        classes = inventory_module.parse_javap(text)
        ids = [t.target_id for t in classes[0].targets]
        self.assertIn("com.example.F#log(java.lang.String[]):void", ids)


@unittest.skipUnless(_java_tool("javac") and _java_tool("jar") and _java_tool("javap"),
                     "needs a JDK (javac/jar/javap)")
class EndToEndJarTests(unittest.TestCase):
    def _build_demo_jar(self, tmp: str) -> str:
        src_dir = os.path.join(tmp, "com", "example")
        os.makedirs(src_dir)
        with open(os.path.join(src_dir, "Greeter.java"), "w", encoding="utf-8") as src:
            src.write(_GREETER_SRC)
        subprocess.run([_java_tool("javac"), "com/example/Greeter.java"], cwd=tmp, check=True)
        jar_path = os.path.join(tmp, "demo.jar")
        subprocess.run([_java_tool("jar"), "cf", jar_path, "com/example/Greeter.class"],
                       cwd=tmp, check=True)
        return jar_path

    def test_inventory_excludes_private_members(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            jar_path = self._build_demo_jar(tmp)
            inventory = inventory_module.generate_inventory(
                coordinate="com.example:demo:1.0.0",
                jar_paths=[jar_path],
                output_dir=os.path.join(tmp, "out"),
                include_package="com.example",
                source_root="",
            )
            ids = {t["id"] for t in inventory["targets"]}
            self.assertIn("com.example.Greeter#greet():java.lang.String", ids)
            self.assertIn("com.example.Greeter#shout():java.lang.String", ids)
            self.assertIn("com.example.Greeter#of(java.lang.String):com.example.Greeter", ids)
            # private secret() must not appear.
            self.assertFalse(any("secret" in i for i in ids))
            self.assertTrue(os.path.isfile(os.path.join(tmp, "out", "api-inventory.json")))
            self.assertTrue(os.path.isfile(os.path.join(tmp, "out", "api-inventory.md")))


if __name__ == "__main__":
    unittest.main()
