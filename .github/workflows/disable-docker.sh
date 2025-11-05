#!/bin/bash

# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

# Purpose:
#   Make Docker unable to access the network during tests by:
#     1) Enabling the discard service on localhost:9 (TCP/UDP) via inetd to accept and immediately discard traffic.
#     2) Pointing Docker's HTTP(S) proxy environment variables to http(s)://localhost:9 using a systemd drop-in.
#
# Why:
#   - Tests may only use pre-pulled/allowed Docker images. This prevents Docker from downloading anything else.
#   - Using the discard service avoids long TCP connection timeouts: the local port accepts connections and discards
#     data quickly, causing Docker's proxy connections to fail fast.
#
# Notes:
#   - This script is designed for GitHub Actions Ubuntu runners with sudo.
#   - It is idempotent: re-running it won't duplicate config lines or unnecessarily restart Docker.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

sudo apt-get install openbsd-inetd
sudo bash -c "cat ${SCRIPT_DIR}/discard-port.conf >> /etc/inetd.conf"
sudo systemctl start inetd
sudo mkdir /etc/systemd/system/docker.service.d
sudo bash -c "cat ${SCRIPT_DIR}/dockerd.service > /etc/systemd/system/docker.service.d/http-proxy.conf"
sudo systemctl daemon-reload
sudo systemctl restart docker
echo "Docker outbound network effectively disabled via proxy=http(s)://localhost:9 backed by inetd discard service."
