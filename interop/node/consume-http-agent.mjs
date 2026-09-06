#!/usr/bin/env node
/**
 * Consume the Clojure echo server with the official @ag-ui/client HttpAgent.
 *
 *   cd interop/node && npm install @ag-ui/client
 *   node consume-http-agent.mjs http://127.0.0.1:8000/
 */
import { HttpAgent } from "@ag-ui/client";

const url = process.argv[2] || "http://127.0.0.1:8000/";
const types = [];

const agent = new HttpAgent({
  url,
  threadId: "interop-ts",
  initialMessages: [{ id: "u1", role: "user", content: "Hello" }],
});

const result = await agent.runAgent({ runId: "interop-ts-run" }, {
  onEvent({ event }) {
    types.push(event.type);
  },
});

console.log(JSON.stringify({ types, newMessages: result.newMessages }, null, 2));
const expected = [
  "RUN_STARTED",
  "TEXT_MESSAGE_START",
  "TEXT_MESSAGE_CONTENT",
  "TEXT_MESSAGE_CONTENT",
  "TEXT_MESSAGE_END",
  "RUN_FINISHED",
];
if (JSON.stringify(types) !== JSON.stringify(expected)) {
  console.error("unexpected types", types);
  process.exit(1);
}
