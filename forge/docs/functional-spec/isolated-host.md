# FS-forge-isolated-host: Disposable Forge host

§GOAL-shorten-issue-to-shipped-metadata §FS-forge-host-requirements

Generated tests run arbitrary library code. They start third-party Docker
containers, open display windows, delete and overwrite files, write into the
home directory, and fill the temporary directory. A maintainer must be able to
run Forge on a host they can throw away, where no run can reach the
workstation's files, sessions, or credentials, and where a broken run is
repaired by resetting the host rather than by cleaning up after it. Forge
therefore ships a disposable-host definition that an operator stands up with a
documented command sequence, and the host it produces passes the
host-requirements gate (§FS-forge-host-requirements) unchanged.

## 1. A virtual machine, not a container

The disposable host is an Incus virtual machine with its own kernel. A
container is not enough: the run is itself a Docker client, the images it pulls
are chosen by the tests and by third-party tools such as Testcontainers, and
nested Docker inside an unprivileged container cannot be relied on to give them
the devices and capabilities they ask for. Docker on the workstation is not an
isolation boundary for the same reason. Inside the VM, Forge runs as an
unprivileged user with access to the VM's own Docker daemon, which keeps the
"no `sudo`" rule of §FS-local-ci-equivalent-verification intact.

## 2. What the definition provides

The definition consists of an Incus profile, an in-VM provisioning script, and
the operator instructions that connect them. The profile declares the VM's
resources and the root-level tools installed on first boot. The provisioning
script runs as the Forge user and installs everything the gate checks for an
issue run: the repository clone, the three GraalVM lanes, the pinned `grype`,
the agent backends, and the Forge Python environment. The lanes are resolved
from the same sources the gate reads — the latest GraalVM GA release, the
repository-pinned 25.0.x version, and the latest Oracle GraalVM EA manifest —
so a freshly provisioned VM passes the gate without a relaxed version check.
The gate is not relaxed inside the VM: a VM that fails it is fixed by
re-provisioning, not by lowering the requirement.

## 3. Nothing crosses from the workstation by default

The workstation's checkout is never mounted into the VM; the VM clones the
repository itself, and generated work leaves it only through the publication
push (§FS-forge-publication-readiness). The definition contains no secrets.
Credentials for GitHub and the agent providers enter the VM only by an explicit
operator step, and the instructions name which files that step copies so an
operator can see exactly what the VM holds.

## 4. Single use

The VM is provisioned once and snapshotted. Before each run the operator
restores the snapshot or launches a fresh VM from the profile, and after a run
the VM is reset or deleted. No state inside the VM is worth keeping between
runs: run metrics, logs, and branches are pushed; everything else is a
by-product that the next run must not see.

## 5. Tunables

CPU count, memory, root-disk size, storage pool, and network are profile values
an operator may change for their machine. The shipped values are the minimums
the definition was validated with for a single-issue run; `--parallelism`
above one needs proportionally more.
