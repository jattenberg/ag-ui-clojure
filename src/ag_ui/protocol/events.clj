(ns ag-ui.protocol.events
  "Canonical AG-UI event types and Clojure field names.

  Wire JSON uses camelCase (schema.json). Internal maps use kebab-case
  keywords. `:type` is always the protocol string discriminator.")

(def event-types
  "The 31 EventType values from https://ag-ui.com/spec/draft/schema.json."
  #{"TEXT_MESSAGE_START"
    "TEXT_MESSAGE_CONTENT"
    "TEXT_MESSAGE_END"
    "TEXT_MESSAGE_CHUNK"
    "TOOL_CALL_START"
    "TOOL_CALL_ARGS"
    "TOOL_CALL_END"
    "TOOL_CALL_CHUNK"
    "TOOL_CALL_RESULT"
    "STATE_SNAPSHOT"
    "STATE_DELTA"
    "MESSAGES_SNAPSHOT"
    "ACTIVITY_SNAPSHOT"
    "ACTIVITY_DELTA"
    "RAW"
    "CUSTOM"
    "RUN_STARTED"
    "RUN_FINISHED"
    "RUN_ERROR"
    "STEP_STARTED"
    "STEP_FINISHED"
    "REASONING_START"
    "REASONING_MESSAGE_START"
    "REASONING_MESSAGE_CONTENT"
    "REASONING_MESSAGE_END"
    "REASONING_MESSAGE_CHUNK"
    "REASONING_END"
    "REASONING_ENCRYPTED_VALUE"
    "SUBAGENT_STARTED"
    "SUBAGENT_FINISHED"
    "SUBAGENT_ERROR"})

(def text-message-roles
  #{"developer" "system" "assistant" "user"})

(def message-roles
  #{"developer" "system" "assistant" "user" "tool" "activity" "reasoning"})

(def run-scoped-types
  "Events that describe the run or conversation as a whole (no subagentRunId)."
  #{"RUN_STARTED" "RUN_FINISHED" "RUN_ERROR" "MESSAGES_SNAPSHOT"})

(def subagent-identity-types
  "Events whose subagentRunId names the invocation itself, not attribution."
  #{"SUBAGENT_STARTED" "SUBAGENT_FINISHED" "SUBAGENT_ERROR"})

(def chunk-types
  #{"TEXT_MESSAGE_CHUNK" "TOOL_CALL_CHUNK" "REASONING_MESSAGE_CHUNK"})

(def retired-thinking-types
  "0.x types the 1.0 draft retires. Compatibility boundary, not current protocol."
  #{"THINKING_START" "THINKING_CONTENT" "THINKING_END"
    "THINKING_TEXT_MESSAGE_START" "THINKING_TEXT_MESSAGE_CONTENT"
    "THINKING_TEXT_MESSAGE_END"})

(defn event-type
  [event]
  (get event :type))
