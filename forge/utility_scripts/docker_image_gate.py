# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Which Docker images a test run of the pre-publication gate brought into the daemon, and which it is blamed for.

CI disables Docker networking after `pullAllowedDockerImages`, so the gate fails
a test run that pulled an image on demand. The daemon is the host's, shared by
every Forge run on it, so while another run is live only an image the test
run's own output names counts as its pull (§FS-local-ci-equivalent-verification.2.2).
"""
from __future__ import annotations

import os
import re
import subprocess

from utility_scripts.issue_locks import live_issues

DOCKER_IMAGE_LIST_COMMAND: list[str] = ["docker", "image", "ls", "--format", "{{.Repository}}:{{.Tag}} {{.ID}}"]
DOCKER_HUB: tuple[str, ...] = ("docker.io", "index.docker.io", "registry-1.docker.io")
FORGE_DIR: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# A mention touches no name character on its left, and on its right none but a sentence's closing period.
BEFORE_NAME: str = r"(?<![\w./-])"
AFTER_NAME: str = r"(?![\w/-]|\.\w)"


def docker_images(repo_path: str) -> set[str]:
    """The daemon's tagged images as `<repository>:<tag> <id>` lines; a daemon that cannot list them raises."""
    try:
        completed = subprocess.run(
            DOCKER_IMAGE_LIST_COMMAND,
            cwd=repo_path,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            check=False,
        )
    except OSError as exc:
        raise RuntimeError(str(exc)) from exc
    if completed.returncode != 0:
        output = (completed.stdout or "").strip()
        details = f" Output: {output}" if output else ""
        raise RuntimeError(f"Cannot list Docker images for local CI no-network-equivalent verification.{details}")
    images: set[str] = set()
    for line in completed.stdout.splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith("<none>:<none>"):
            images.add(stripped)
    return images


def other_live_runs(issue_number: int | None) -> set[int]:
    """The issues of the other Forge runs live on this host now; `issue_number` is this run's own, if any."""
    return {issue for issue in live_issues(FORGE_DIR) if issue != issue_number}


def names_image(output: str, image: str) -> bool:
    """Whether `output` names the repository of `image`, a `<repository>:<tag> <id>` line, with or without
    its registry and tag: `nginx`, `library/nginx` and `docker.io/library/nginx:1-alpine-slim` all name
    `docker.io/library/nginx`."""
    repository: str = image.split(" ", 1)[0].rsplit(":", 1)[0]
    first, _, rest = repository.partition("/")
    is_registry: bool = bool(rest) and ("." in first or ":" in first or first == "localhost")
    registry, path = (first, rest) if is_registry else ("docker.io", repository)
    if registry in DOCKER_HUB:
        registries: str = "|".join(map(re.escape, DOCKER_HUB))
        prefix: str = rf"(?:(?:{registries})/)?"
        # An official image is `library/<name>`, whose familiar name drops that namespace.
        official: str = path.removeprefix("library/")
        name: str = rf"(?:library/)?{re.escape(official)}" if "/" not in official else re.escape(path)
    else:
        prefix, name = rf"(?:{re.escape(registry)}/)?", re.escape(path)
    return re.search(BEFORE_NAME + prefix + name + AFTER_NAME, output) is not None


def blamed_images(new_images: list[str], output: str, other_runs: set[int]) -> list[str]:
    """The new images that fail the test run: each one while no other run was live, else those `output` names."""
    return [image for image in new_images if not other_runs or names_image(output, image)]


def issue_list(issues: set[int]) -> str:
    """`#7, #9` for the issues 9 and 7."""
    return ", ".join(f"#{issue}" for issue in sorted(issues))


def concurrent_pull_warning(image: str, other_runs: set[int]) -> str:
    """The warning for a new image that the test run's output does not name while other runs were live."""
    return (f"WARNING: Docker image {image} appeared during the no-network-equivalent test gate, but the test "
            f"output does not name it; a concurrent Forge run may have pulled it (live runs: "
            f"{issue_list(other_runs)}). The gate does not fail on it.")
