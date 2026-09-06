(ns ag-ui.serialization.fields
  "Bidirectional mapping between wire camelCase and Clojure kebab-case.

  Derived from schema.json field names, not from any SDK source."
  (:require [clojure.string :as str]))

(def wire->clj
  {"type" :type
   "timestamp" :timestamp
   "rawEvent" :raw-event
   "metadata" :metadata
   "threadId" :thread-id
   "runId" :run-id
   "parentRunId" :parent-run-id
   "protocolVersion" :protocol-version
   "input" :input
   "messageId" :message-id
   "role" :role
   "name" :name
   "delta" :delta
   "toolCallId" :tool-call-id
   "toolCallName" :tool-call-name
   "parentMessageId" :parent-message-id
   "content" :content
   "error" :error
   "encryptedValue" :encrypted-value
   "snapshot" :snapshot
   "messages" :messages
   "activityType" :activity-type
   "patch" :patch
   "replace" :replace
   "event" :event
   "source" :source
   "value" :value
   "result" :result
   "outcome" :outcome
   "usage" :usage
   "message" :message
   "code" :code
   "stepName" :step-name
   "subtype" :subtype
   "entityId" :entity-id
   "subagentRunId" :subagent-run-id
   "parentSubagentRunId" :parent-subagent-run-id
   "parentToolCallId" :parent-tool-call-id
   "state" :state
   "tools" :tools
   "context" :context
   "forwardedProps" :forwarded-props
   "resume" :resume
   "interruptId" :interrupt-id
   "status" :status
   "payload" :payload
   "id" :id
   "reason" :reason
   "toolCalls" :tool-calls
   "function" :function
   "arguments" :arguments
   "description" :description
   "parameters" :parameters
   "provider" :provider
   "model" :model
   "inputTokens" :input-tokens
   "outputTokens" :output-tokens
   "totalTokens" :total-tokens
   "reasoningTokens" :reasoning-tokens
   "cachedInputTokens" :cached-input-tokens
   "interrupts" :interrupts
   "responseSchema" :response-schema
   "expiresAt" :expires-at
   "mimeType" :mime-type
   "text" :text
   "op" :op
   "path" :path
   "from" :from})

(def clj->wire
  (into {} (map (fn [[w k]] [k w]) wire->clj)))

(defn- camelize-unknown
  [s]
  (str/replace s #"-([a-z])" (fn [[_ c]] (str/upper-case c))))

(defn key->wire
  [k]
  (cond
    (string? k) k
    (keyword? k) (or (clj->wire k)
                     (let [n (name k)]
                       (if (contains? wire->clj n)
                         n
                         (camelize-unknown n))))
    :else (str k)))

(defn- kebabize
  [s]
  (-> s
      (str/replace #"([a-z0-9])([A-Z])" "$1-$2")
      str/lower-case))

(defn key->clj
  [k]
  (let [s (cond (keyword? k) (name k)
                (string? k) k
                :else (str k))]
    (or (wire->clj s)
        (keyword (kebabize s)))))
