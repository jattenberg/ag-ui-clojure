# Language-neutral fixtures

`manifest.json` lists every stream. Other implementations can consume the JSONL files without Clojure:

1. Parse each `path` as UTF-8 JSONL (one AG-UI event object per non-empty, non-`#` line).
2. Field names are camelCase as in `spec/draft/schema.json`.
3. `status: valid` streams must pass schema + lifecycle.
4. `status: malformed` streams must be rejected (decode, structure, schema, or lifecycle).

Resume cases that are not event streams live in `fixtures/runs/*-resume.json`.

Recorded official-Python SSE: `fixtures/interop/python-ag-ui-protocol.sse`.
