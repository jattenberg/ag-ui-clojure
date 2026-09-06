# Malformed fixtures

These files are **invalid** AG-UI. Conformance tools must reject them.

| File | Why invalid | Spec basis |
| --- | --- | --- |
| `missing-required-field.json` | `TEXT_MESSAGE_CONTENT` without `messageId` | schema: required `type`, `messageId`, `delta` |
| `unknown-event-type.json` | `NOT_A_REAL_EVENT` is not an `EventType` | schema `EventType` enum (authoring/producer) |
| `invalid-field-type.json` | `messageId` is a number | schema: `messageId` is string; processing model: malformed known value is fatal |
| `invalid-lifecycle.jsonl` | `RUN_FINISHED` while a text message is still open | streaming pattern: every opened item must close before the run finishes |
| `tool-end-without-start.jsonl` | `TOOL_CALL_END` for an id that is not open | streaming pattern |
| `malformed-json.json` | not JSON | transport/decode failure, not a protocol event |

Runtime consumers **drop** unknown *future* event types (processing model). Authoring validation and this fixture suite treat unknown types as producer errors.
