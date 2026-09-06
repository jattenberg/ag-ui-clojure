# Conformance

The suite is meant to be reusable by other implementations, not only Clojure tests.

## Fixtures

Valid streams (JSONL, one wire event per line):

- `fixtures/events/basic-run.jsonl`
- `fixtures/events/text-stream.jsonl`
- `fixtures/events/text-chunks.jsonl`
- `fixtures/events/tool-call.jsonl`
- `fixtures/events/state-update.jsonl`
- `fixtures/events/interrupt.jsonl`
- `fixtures/events/error.jsonl`
- `fixtures/events/custom-event.jsonl`
- `fixtures/events/steps.jsonl`

Malformed cases and rationale: `fixtures/malformed/README.md`.

Run input example: `fixtures/runs/basic-input.json`.

JSON Schema pin: `spec/draft/schema.json` (from https://ag-ui.com/spec/draft/schema.json).

## CLI

```bash
clojure -M:conformance
clojure -M:conformance --endpoint http://127.0.0.1:8000/
```

The CLI:

1. Loads valid fixtures, validates structure, expands chunks, checks lifecycle, round-trips JSON, reduces state.
2. Asserts malformed fixtures are rejected.
3. Optionally POSTs a `RunAgentInput` to a live SSE endpoint.

## Tests

```bash
clojure -M:test
```

Property tests cover JSON round-trip of generated `RUN_STARTED` events and generated text-message runs (lifecycle + content concatenation).
