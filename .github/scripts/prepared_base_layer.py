#!/usr/bin/env python3
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Transfer the complete common base between matching CI jobs. §FS-tests.3.6"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import os
import subprocess
import tarfile
from pathlib import Path


def current_identity() -> dict[str, str]:
    """Identify the checked-out revision, runner, and installed GraalVM build."""
    release: Path = Path(os.environ["JAVA_HOME"]) / "release"
    return {
        "revision": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
        "nativeImageVersion": subprocess.check_output(["native-image", "--version"], text=True).strip(),
        "jdkReleaseSha256": hashlib.sha256(release.read_bytes()).hexdigest(),
        "os": os.environ["RUNNER_OS"],
        "arch": os.environ["RUNNER_ARCH"],
        "nativeImageMode": os.environ["GVM_TCK_NATIVE_IMAGE_MODE"],
    }


def validate_base(directory: Path, identity: dict[str, str]) -> None:
    """Reject incomplete layers or a manifest from a different toolchain/mode."""
    for name in ("base-layer.nil", "manifest.json"):
        if not (directory / name).is_file():
            raise ValueError(f"Prepared base is missing {name}: {directory}")
    manifest: dict = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
    for key in ("nativeImageVersion", "nativeImageMode"):
        if manifest.get(key) != identity[key]:
            raise ValueError(f"Prepared base manifest does not match {key}")


def pack(directory: Path, archive: Path) -> None:
    identity: dict[str, str] = current_identity()
    validate_base(directory, identity)
    provenance: bytes = json.dumps(identity, sort_keys=True).encode("utf-8")
    with tarfile.open(archive, "w") as output:
        entry: tarfile.TarInfo = tarfile.TarInfo("provenance.json")
        entry.size = len(provenance)
        output.addfile(entry, io.BytesIO(provenance))
        output.add(directory, arcname="base-layer")


def restore(archive: Path, destination: Path) -> Path:
    identity: dict[str, str] = current_identity()
    with tarfile.open(archive, "r") as source:
        provenance: dict = json.load(source.extractfile("provenance.json"))
        if provenance != identity:
            changed: list[str] = sorted(key for key in provenance.keys() | identity.keys()
                                        if provenance.get(key) != identity.get(key))
            raise ValueError("Prepared base does not match this shard: " + ", ".join(changed))
        destination.mkdir(parents=True, exist_ok=False)
        source.extractall(destination, filter="data")
    directory: Path = (destination / "base-layer").resolve()
    validate_base(directory, identity)
    return directory


def main() -> None:
    parser: argparse.ArgumentParser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    pack_parser: argparse.ArgumentParser = commands.add_parser("pack")
    pack_parser.add_argument("--base-layer-dir", required=True, type=Path)
    pack_parser.add_argument("--archive", required=True, type=Path)
    restore_parser: argparse.ArgumentParser = commands.add_parser("restore")
    restore_parser.add_argument("--archive", required=True, type=Path)
    restore_parser.add_argument("--destination", required=True, type=Path)
    args: argparse.Namespace = parser.parse_args()
    if args.command == "pack":
        pack(args.base_layer_dir, args.archive)
    else:
        directory: Path = restore(args.archive, args.destination)
        with Path(os.environ["GITHUB_ENV"]).open("a", encoding="utf-8") as environment:
            environment.write(f"GVM_TCK_BASE_LAYER_DIR={directory}\n")
        print(f"Restored prepared common base at {directory}")


if __name__ == "__main__":
    main()
