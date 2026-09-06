# Upstream offer

This repository is **not** an official AG-UI SDK. The reusable piece for first-party maintainers is the language-neutral fixture pack:

- [`fixtures/manifest.json`](../fixtures/manifest.json) — index, producer vs consumer expectations
- [`fixtures/events/`](../fixtures/events/) and [`fixtures/malformed/`](../fixtures/malformed/) — camelCase JSONL
- [`fixtures/README.md`](../fixtures/README.md) — how to consume without Clojure

Suggested contribution: copy or submodule that tree into `ag-ui-protocol/ag-ui` as a conformance corpus, without taking the Clojure runtime.

Suggested GitHub issue title: `Language-neutral AG-UI fixture pack (unofficial Clojure interpreter)`

Body:

This is an unofficial offer, not a claim of first-party status.

https://github.com/jattenberg/ag-ui-clojure is an independent interpreter of the 1.0 draft + pinned schema.json. The reusable piece is a language-neutral JSONL fixture pack (fixtures/manifest.json, fixtures/events, fixtures/malformed). Producer profile = authoring/schema; consumer profile = processing model.

If useful as an upstream conformance corpus, the pack can be copied without the Clojure runtime.

