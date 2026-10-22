# Running Forge in a disposable Incus VM

Generated tests run arbitrary library code: they start third-party Docker
containers, open windows, delete files, and write into the home directory.
This directory lets you run Forge inside a throwaway virtual machine so none of
that reaches your workstation (§FS-forge-isolated-host). The VM passes the
same host-requirements gate as a workstation (§FS-forge-host-requirements);
nothing is relaxed inside it.

Files:

- `forge-vm.yaml` — the Incus profile: VM resources plus the cloud-init that
  installs the root-level tools (Docker, `gh`, Node.js, `grype`) on first boot.
- `provision.sh` — runs inside the VM as the `forge` user: clones the
  repository, installs the three GraalVM lanes from the sources the gate reads,
  the `pi` and `codex` CLIs, and the Forge Python environment.

All commands below run on the workstation unless they are prefixed with
`incus exec`.

## 1. Host setup (once per workstation)

Install [Incus](https://linuxcontainers.org/incus/docs/main/installing/) and
initialize it with the defaults, which create the `default` storage pool and
the `incusbr0` network the profile refers to. VMs need `/dev/kvm`.

```console
sudo incus admin init --minimal
sudo usermod -aG incus-admin "$USER"   # then log out and in
incus profile create forge
incus profile edit forge < forge/incus/forge-vm.yaml
```

A workstation that also runs Docker needs one more step: Docker sets the
host's `FORWARD` policy to `DROP`, which leaves VMs on `incusbr0` with DHCP and
DNS but no route out, so cloud-init fails on the first package download.
Allow the bridge through Docker's own chain, as the
[Incus documentation](https://linuxcontainers.org/incus/docs/main/howto/network_bridge_firewalld/#prevent-connectivity-issues-with-incus-and-docker)
describes, and make the rules persistent on your distribution:

```console
sudo iptables -I DOCKER-USER -i incusbr0 -j ACCEPT
sudo iptables -I DOCKER-USER -o incusbr0 -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT
```

To use a different pool, network, or size, edit the device after import:
`incus profile device set forge root pool=<pool>` or `size=128GiB`. The
shipped resources (4 CPUs, 16 GiB, 64 GiB disk) are the validated minimums for
one issue run; `--parallelism` above one needs proportionally more
(§FS-forge-isolated-host.5).

## 2. Launch and provision

```console
incus launch images:ubuntu/24.04/cloud forge-vm --vm --profile forge
incus exec forge-vm -- cloud-init status --wait
incus file push forge/incus/provision.sh forge-vm/home/forge/provision.sh
incus exec forge-vm -- sudo -iu forge bash /home/forge/provision.sh
```

`provision.sh` is idempotent: re-run it after a GraalVM release moves to pick
up the new lanes. Override `FORGE_REPO_URL`, `FORGE_REPO_BRANCH`, or
`FORGE_AGENT_PACKAGES` in its environment to clone a fork or branch, or to skip
the agent CLIs.

## 3. Copy credentials in

The VM holds no secrets until you put them there (§FS-forge-isolated-host.3).
Copy exactly the files the gate probes, then hand them to the `forge` user:

```console
incus file push -p ~/.config/gh/hosts.yml forge-vm/home/forge/.config/gh/hosts.yml
incus file push -p ~/.codex/auth.json forge-vm/home/forge/.codex/auth.json
incus file push -p -r ~/.pi/agent/ forge-vm/home/forge/.pi/
incus exec forge-vm -- chown -R forge:forge /home/forge
```

Alternatively log in from inside the VM, for example
`incus exec forge-vm -- sudo -iu forge gh auth login` for a device-code flow.

## 4. Verify, snapshot, run

Validate the host from a login shell so the lanes written by `provision.sh`
to `~/.forge-vm.env` are in effect, then snapshot the clean state:

```console
incus exec forge-vm -- sudo -iu forge bash -lc \
  'cd ~/graalvm-reachability-metadata/forge && "$PYTHON_BIN" utility_scripts/host_requirements.py --forge-dir . --verbose'
incus snapshot create forge-vm provisioned
```

Run Forge exactly as on a workstation; the gate runs first and prints its
report:

```console
incus exec forge-vm -- sudo -iu forge bash -lc \
  'cd ~/graalvm-reachability-metadata/forge && ./do-work.sh --once --new-limit 1'
```

For an interactive shell use `incus exec forge-vm -- sudo -iu forge`.

## 5. Reset or destroy

The VM is single-use (§FS-forge-isolated-host.4). Generated branches and run
metrics leave it through the publication push, so nothing inside it needs to
survive a run:

```console
incus snapshot restore forge-vm provisioned   # back to the clean state
incus delete -f forge-vm                      # or throw the VM away
```

To keep a provisioned base for many fresh VMs, publish it as an image once and
launch from that instead of the `images:` remote:

```console
incus stop forge-vm && incus publish forge-vm --alias forge-base && incus start forge-vm
incus launch forge-base forge-vm-2 --vm --profile forge
```
