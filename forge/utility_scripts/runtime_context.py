# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import platform
import subprocess
import sys


def collect_runtime_environment_summary() -> str:
    """Return prompt-ready facts about the current Forge execution environment."""
    facts = [
        ("OS", platform.platform()),
        ("OS name", platform.system()),
        ("OS release", platform.release()),
        ("Architecture", platform.machine()),
        ("Python", sys.version.split()[0]),
        ("JAVA_HOME", os.environ.get("JAVA_HOME") or "not set"),
        ("GRAALVM_HOME", os.environ.get("GRAALVM_HOME") or "not set"),
        ("java -version", _command_version(_java_command())),
        ("native-image --version", _command_version(_native_image_command())),
    ]
    return "\n".join("- {key}: {value}".format(key=key, value=value) for key, value in facts)


def _java_command() -> list[str]:
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        return [os.path.join(java_home, "bin", "java"), "-version"]
    return ["java", "-version"]


def _native_image_command() -> list[str]:
    graalvm_home = os.environ.get("GRAALVM_HOME") or os.environ.get("JAVA_HOME")
    if graalvm_home:
        return [os.path.join(graalvm_home, "bin", "native-image"), "--version"]
    return ["native-image", "--version"]


def _command_version(command: list[str]) -> str:
    try:
        result = subprocess.run(
            command,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
            timeout=10,
        )
    except (FileNotFoundError, subprocess.TimeoutExpired) as error:
        return "unavailable ({error})".format(error=error)

    lines = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    if not lines:
        return "unavailable (exit_code={code})".format(code=result.returncode)
    suffix = "" if result.returncode == 0 else " (exit_code={code})".format(code=result.returncode)
    return " | ".join(lines[:3]) + suffix
