#!/usr/bin/env python3
"""Minimal AG-UI HTTP+SSE producer using the official Python encoder when installed.

This is an interoperability probe, not a production agent. It emits the same
basic-run sequence as fixtures/events/basic-run.jsonl.

    python3 interop/python/echo_server.py 18090
    clojure -M:client http://127.0.0.1:18090/ Hello
"""

from __future__ import annotations

import json
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def events():
    built = None
    try:
        from ag_ui.core import (
            EventType,
            RunFinishedEvent,
            RunStartedEvent,
            TextMessageContentEvent,
            TextMessageEndEvent,
            TextMessageStartEvent,
        )
        from ag_ui.encoder import EventEncoder

        encoder = EventEncoder()
        seq = [
            RunStartedEvent(type=EventType.RUN_STARTED, thread_id="thread_basic", run_id="run_basic"),
            TextMessageStartEvent(
                type=EventType.TEXT_MESSAGE_START, message_id="msg_1", role="assistant"
            ),
            TextMessageContentEvent(
                type=EventType.TEXT_MESSAGE_CONTENT, message_id="msg_1", delta="Hello"
            ),
            TextMessageContentEvent(
                type=EventType.TEXT_MESSAGE_CONTENT, message_id="msg_1", delta=", world."
            ),
            TextMessageEndEvent(type=EventType.TEXT_MESSAGE_END, message_id="msg_1"),
            RunFinishedEvent(
                type=EventType.RUN_FINISHED, thread_id="thread_basic", run_id="run_basic"
            ),
        ]
        return "".join(encoder.encode(ev) for ev in seq), "ag_ui"
    except Exception:
        payload = [
            {"type": "RUN_STARTED", "threadId": "thread_basic", "runId": "run_basic"},
            {"type": "TEXT_MESSAGE_START", "messageId": "msg_1", "role": "assistant"},
            {"type": "TEXT_MESSAGE_CONTENT", "messageId": "msg_1", "delta": "Hello"},
            {"type": "TEXT_MESSAGE_CONTENT", "messageId": "msg_1", "delta": ", world."},
            {"type": "TEXT_MESSAGE_END", "messageId": "msg_1"},
            {"type": "RUN_FINISHED", "threadId": "thread_basic", "runId": "run_basic"},
        ]
        body = "".join("data: " + json.dumps(e, separators=(",", ":")) + "\n\n" for e in payload)
        return body, "fallback"


SSE, SOURCE = events()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        sys.stderr.write("%s [%s]\n" % (SOURCE, fmt % args))

    def do_GET(self):
        if self.path == "/health":
            self.send_response(200)
            self.send_header("Content-Type", "text/plain")
            self.end_headers()
            self.wfile.write(b"ok")
            return
        self.send_response(404)
        self.end_headers()

    def do_POST(self):
        accept = self.headers.get("Accept", "")
        if "text/event-stream" not in accept:
            self.send_response(406)
            self.end_headers()
            return
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream")
        self.send_header("Cache-Control", "no-cache")
        self.end_headers()
        self.wfile.write(SSE.encode("utf-8"))


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18090
    print(f"python AG-UI echo ({SOURCE}) on http://127.0.0.1:{port}/", file=sys.stderr)
    ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()


if __name__ == "__main__":
    main()
