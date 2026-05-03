#!/bin/bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

set -euo pipefail

# Undo the Docker proxy drop-in installed by disable-docker.sh so a local Forge
# process can verify multiple PRs without carrying CI's no-network setting into
# the next verification run.
sudo rm -f /etc/systemd/system/docker.service.d/http-proxy.conf
if [[ -d /etc/systemd/system/docker.service.d ]]; then
    sudo rmdir --ignore-fail-on-non-empty /etc/systemd/system/docker.service.d || true
fi
sudo systemctl daemon-reload
sudo systemctl restart docker
echo "Docker outbound network restored."
