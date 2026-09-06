#!/usr/bin/env python3
"""Emit a basic AG-UI run as JSONL (camelCase wire format).

If ag_ui is installed (pip install ag-ui-protocol), events are built from
the Python models and dumped with by_alias=True / equivalent camelCase.
Otherwise this script writes the same JSON the encoder documentation shows.
"""

from __future__ import annotations

import json
import sys


def events_dict():
    return [
        {"type": "RUN_STARTED", "threadId": "thread_basic", "runId": "run_basic"},
        {"type": "TEXT_MESSAGE_START", "messageId": "msg_1", "role": "assistant"},
        {"type": "TEXT_MESSAGE_CONTENT", "messageId": "msg_1", "delta": "Hello"},
        {"type": "TEXT_MESSAGE_CONTENT", "messageId": "msg_1", "delta": ", world."},
        {"type": "TEXT_MESSAGE_END", "messageId": "msg_1"},
        {"type": "RUN_FINISHED", "threadId": "thread_basic", "runId": "run_basic"},
    ]


def try_ag_ui():
    try:
        from ag_ui.core import (
            EventType,
            RunFinishedEvent,
            RunStartedEvent,
            TextMessageContentEvent,
            TextMessageEndEvent,
            TextMessageStartEvent,
        )
    except Exception:
        return None

    built = [
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
        RunFinishedEvent(type=EventType.RUN_FINISHED, thread_id="thread_basic", run_id="run_basic"),
    ]
    out = []
    for ev in built:
        if hasattr(ev, "model_dump"):
            out.append(ev.model_dump(by_alias=True, exclude_none=True))
        else:
            out.append(json.loads(ev.json(by_alias=True, exclude_none=True)))
    return out


def main():
    events = try_ag_ui()
    source = "ag_ui" if events is not None else "fallback"
    if events is None:
        events = events_dict()
    print(f"# encoded via {source}", file=sys.stderr)
    for event in events:
        json.dump(event, sys.stdout, separators=(",", ":"))
        sys.stdout.write("\n")


if __name__ == "__main__":
    main()
