# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import queue
import subprocess
import sys
import threading
import time


class JSONLineReader(threading.Thread):
    """Collect line-delimited app-server output without blocking the caller forever."""

    def __init__(self, stream):
        super().__init__(daemon=True)
        self._stream = stream
        self._queue: queue.Queue[str | None] = queue.Queue()

    def run(self) -> None:
        for line in self._stream:
            self._queue.put(line)
        self._queue.put(None)

    def read_line(self, timeout: float) -> str | None:
        try:
            return self._queue.get(timeout=timeout)
        except queue.Empty as exc:
            raise TimeoutError("Timed out while waiting for app-server output.") from exc


class CodexAppServerClient:
    """Small helper for Codex app-server thread lifecycle calls."""

    def __init__(
            self,
            model_name: str,
            working_dir: str,
            timeout: int = 600,
            reasoning_effort: str = "high",
    ):
        self._model_name = model_name
        self._working_dir = os.path.abspath(working_dir)
        self._timeout = timeout
        self._reasoning_effort = reasoning_effort

    def start_thread(self) -> dict:
        params = self._build_common_thread_params()
        params["experimentalRawEvents"] = False
        result = self._request("thread/start", params)
        return self._extract_thread(result, "thread/start")

    def resume_thread(self, thread_id: str) -> dict:
        params = self._build_common_thread_params()
        params["threadId"] = thread_id
        result = self._request("thread/resume", params)
        return self._extract_thread(result, "thread/resume")

    def fork_thread(self, thread_id: str) -> dict:
        params = self._build_common_thread_params()
        params["threadId"] = thread_id
        result = self._request("thread/fork", params)
        return self._extract_thread(result, "thread/fork")

    def compact_thread(self, thread_id: str) -> None:
        self._request("thread/compact/start", {"threadId": thread_id})

    def fork_and_compact_thread(self, thread_id: str) -> dict:
        try:
            process = subprocess.Popen(
                ["codex", "app-server", "--listen", "stdio://"],
                cwd=self._working_dir,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
            )
        except OSError as exc:
            raise RuntimeError("Failed to start Codex app-server.") from exc

        try:
            reader = JSONLineReader(process.stdout)
            reader.start()
            self._send(process.stdin, {
                "id": 1,
                "method": "initialize",
                "params": {
                    "clientInfo": {"name": "metadata-forge", "version": "0.1"},
                    "capabilities": {"experimentalApi": True, "optOutNotificationMethods": []},
                },
            })
            self._wait_for_response(reader, process, 1, "initialize")

            params = self._build_common_thread_params()
            params["threadId"] = thread_id
            self._send(process.stdin, {"id": 2, "method": "thread/fork", "params": params})
            fork_result = self._wait_for_response(reader, process, 2, "thread/fork")
            child_thread = self._extract_thread(fork_result, "thread/fork")

            self._send(
                process.stdin,
                {"id": 3, "method": "thread/compact/start", "params": {"threadId": child_thread["id"]}},
            )
            self._wait_for_response(reader, process, 3, "thread/compact/start")
            return child_thread
        finally:
            process.stdin.close()
            process.stdout.close()
            process.kill()
            process.wait(timeout=5)

    def _build_common_thread_params(self) -> dict:
        return {
            "model": self._model_name,
            "cwd": self._working_dir,
            "approvalPolicy": "never",
            "sandbox": "danger-full-access",
            "config": {"reasoning.effort": self._reasoning_effort},
            "persistExtendedHistory": True,
        }

    def _request(self, method: str, params: dict) -> dict:
        try:
            process = subprocess.Popen(
                ["codex", "app-server", "--listen", "stdio://"],
                cwd=self._working_dir,
                stdin=subprocess.PIPE,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
            )
        except OSError as exc:
            raise RuntimeError("Failed to start Codex app-server.") from exc

        try:
            reader = JSONLineReader(process.stdout)
            reader.start()

            self._send(process.stdin, {
                "id": 1,
                "method": "initialize",
                "params": {
                    "clientInfo": {"name": "metadata-forge", "version": "0.1"},
                    "capabilities": {"experimentalApi": True, "optOutNotificationMethods": []},
                },
            })
            self._wait_for_response(reader, process, 1, "initialize")

            self._send(process.stdin, {"id": 2, "method": method, "params": params})
            return self._wait_for_response(reader, process, 2, method)
        finally:
            process.stdin.close()
            process.stdout.close()
            process.kill()
            process.wait(timeout=5)

    @staticmethod
    def _send(stream, payload: dict) -> None:
        stream.write(json.dumps(payload) + "\n")
        stream.flush()

    def _wait_for_response(
            self,
            reader: JSONLineReader,
            process: subprocess.Popen,
            request_id: int,
            method: str,
    ) -> dict:
        deadline = time.monotonic() + self._timeout
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise RuntimeError(f"Codex app-server timed out during `{method}`.")

            line = reader.read_line(remaining)
            if line is None:
                raise RuntimeError(
                    f"Codex app-server exited before responding to `{method}` "
                    f"(exit code {process.poll()})."
                )

            stripped = line.strip()
            if not stripped:
                continue

            try:
                payload = json.loads(stripped)
            except json.JSONDecodeError:
                print(stripped, file=sys.stderr)
                continue

            if payload.get("id") != request_id:
                continue

            if "error" in payload:
                error_message = payload["error"].get("message", f"`{method}` failed.")
                raise RuntimeError(f"Codex app-server `{method}` failed: {error_message}")

            return payload.get("result", {})

    @staticmethod
    def _extract_thread(result: dict, method: str) -> dict:
        thread = result.get("thread")
        if isinstance(thread, dict) and thread.get("id"):
            return thread
        raise RuntimeError(f"Codex app-server `{method}` returned no thread id.")
