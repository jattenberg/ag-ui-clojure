# Interoperability

This project is an independent interpreter of AG-UI. Interop tests exist to see whether other implementations emit/consume the **same wire**.

## A. External client → Clojure server

Start the echo server:

```bash
clojure -M:server 8000
# or: bb server 8000
```

POST from any AG-UI client (curl is the baseline; CopilotKit `HttpAgent` and `@ag-ui/client` are the canonical TS clients):

```bash
curl -N -X POST http://127.0.0.1:8000/ \
  -H "Content-Type: application/json" \
  -H "Accept: text/event-stream" \
  -d @fixtures/runs/basic-input.json
```

Expected event types, in order:

`RUN_STARTED`, `TEXT_MESSAGE_START`, `TEXT_MESSAGE_CONTENT`, `TEXT_MESSAGE_CONTENT`, `TEXT_MESSAGE_END`, `RUN_FINISHED`

Prompt keywords the echo agent understands: `tool`, `state`, `interrupt`, `error`, `custom`.

Node (`undici`, no AG-UI SDK required — still a valid SSE client):

```bash
node interop/node/post-echo.mjs http://127.0.0.1:8000/
```

TypeScript official client (when `@ag-ui/client` is installed):

```bash
cd interop/node && npm install @ag-ui/client
node consume-http-agent.mjs http://127.0.0.1:8000/
```

## B. Clojure client → external server

Against this repo’s server:

```bash
clojure -M:server 8000
clojure -M:client http://127.0.0.1:8000/ Hello
bb client http://127.0.0.1:8000/ Hello
```

Against a Python AG-UI server (upstream quickstart / `ag-ui-protocol` encoder):

```bash
# example shape from https://docs.ag-ui.com/quickstart/server.md
# poetry run dev   # in an ag-ui integration server
clojure -M:client http://127.0.0.1:8000/ Hello
```

Python encoder → Clojure decoder (no HTTP):

```bash
python3 interop/python/encode_events.py | clojure -M:conformance
# or compare JSONL:
python3 interop/python/encode_events.py > /tmp/py.jsonl
diff -u fixtures/events/basic-run.jsonl /tmp/py.jsonl
```

`encode_events.py` uses `ag_ui` if `pip install ag-ui-protocol` succeeds; otherwise it writes the same camelCase JSON the encoder documentation specifies.

## Verified (2026-09-06, this machine)

- Official Python `ag-ui-protocol` models (`model_dump(by_alias=True)`) produce **byte-identical JSONL** to `fixtures/events/basic-run.jsonl`.
- Official `@ag-ui/client` `HttpAgent` consumed the Clojure echo server and assembled `Hello, world.` from the streamed text events.
- `node interop/node/post-echo.mjs` and `clojure -M:client` consumed the same server.

Python:

```bash
python3 -m venv .venv
.venv/bin/pip install ag-ui-protocol
.venv/bin/python interop/python/encode_events.py | diff -u fixtures/events/basic-run.jsonl -
```

TypeScript:

```bash
clojure -M:server 8000
cd interop/node && npm install @ag-ui/client
node consume-http-agent.mjs http://127.0.0.1:8000/
```

- Clojure server produces SSE `data:` frames whose JSON validates against this repo’s authoring validator and the pinned schema field names.
- Clojure client parses SSE from the echo server and from curl-produced fixtures.
- Python `EventEncoder` documentation output (`data: {"type":"TEXT_MESSAGE_CONTENT","messageId":...}`) matches this encoder’s field naming.

Live tests against CopilotKit Dojo or a third-party cloud agent are **environment-dependent** and are not run in CI. Commands above are the reproduction path.
