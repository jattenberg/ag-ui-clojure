(ns ag-ui.protocol.schema
  "Structural field tables derived from spec/draft/schema.json (2026-09-06 pin).

  Authoritative structure is the JSON Schema. This table is a Clojure
  projection used for independent validation — keep it aligned with the pin.")

(def json-null-ok
  "Fields whose schema admits any JSON value, including null."
  #{:raw-event :result :value :event :snapshot :payload :state
    :forwarded-props :content :delta :parameters})

(def field-type
  "Declared types for known fields. :any is any JSON value."
  {:type :string
   :timestamp :integer
   :raw-event :any
   :metadata :object
   :thread-id :string
   :run-id :string
   :parent-run-id :string
   :protocol-version :string
   :input :object
   :message-id :string
   :role :string
   :name :string
   :delta :string
   :tool-call-id :string
   :tool-call-name :string
   :parent-message-id :string
   :content :any
   :error :string
   :encrypted-value :string
   :snapshot :any
   :messages :array
   :activity-type :string
   :patch :array
   :replace :boolean
   :event :any
   :source :string
   :value :any
   :result :any
   :outcome :object
   :usage :array
   :message :string
   :code :string
   :step-name :string
   :subtype :string
   :entity-id :string
   :subagent-run-id :string
   :parent-subagent-run-id :string
   :parent-tool-call-id :string
   :state :any
   :tools :array
   :context :array
   :forwarded-props :any
   :resume :array
   :interrupt-id :string
   :status :string
   :payload :any
   :id :string
   :reason :string})

(def base-optional
  #{:timestamp :raw-event :metadata})

(def attributable-optional
  #{:subagent-run-id})

(defn- opt
  [& sets]
  (into #{} cat sets))

(def event-specs
  "Per-type required and optional fields (kebab). Closed objects per schema."
  {"TEXT_MESSAGE_START"
   {:required #{:type :message-id}
    :optional (opt base-optional attributable-optional #{:role :name})
    :enums {:role #{"developer" "system" "assistant" "user"}}}

   "TEXT_MESSAGE_CONTENT"
   {:required #{:type :message-id :delta}
    :optional (opt base-optional attributable-optional)
    :string-fields #{:delta}}

   "TEXT_MESSAGE_END"
   {:required #{:type :message-id}
    :optional (opt base-optional attributable-optional)}

   "TEXT_MESSAGE_CHUNK"
   {:required #{:type}
    :optional (opt base-optional attributable-optional #{:message-id :role :delta :name})
    :enums {:role #{"developer" "system" "assistant" "user"}}}

   "TOOL_CALL_START"
   {:required #{:type :tool-call-id :tool-call-name}
    :optional (opt base-optional attributable-optional #{:parent-message-id})}

   "TOOL_CALL_ARGS"
   {:required #{:type :tool-call-id :delta}
    :optional (opt base-optional attributable-optional)}

   "TOOL_CALL_END"
   {:required #{:type :tool-call-id}
    :optional (opt base-optional attributable-optional)}

   "TOOL_CALL_CHUNK"
   {:required #{:type}
    :optional (opt base-optional attributable-optional
                    #{:tool-call-id :tool-call-name :parent-message-id :delta})}

   "TOOL_CALL_RESULT"
   {:required #{:type :message-id :tool-call-id :content}
    :optional (opt base-optional attributable-optional #{:role :error :encrypted-value})
    :enums {:role #{"tool"}}
    :content-type :string}

   "STATE_SNAPSHOT"
   {:required #{:type :snapshot}
    :optional (opt base-optional attributable-optional)}

   "STATE_DELTA"
   {:required #{:type :delta}
    :optional (opt base-optional attributable-optional)
    :delta-is-patch true}

   "MESSAGES_SNAPSHOT"
   {:required #{:type :messages}
    :optional base-optional}

   "ACTIVITY_SNAPSHOT"
   {:required #{:type :message-id :activity-type :content}
    :optional (opt base-optional attributable-optional #{:replace})
    :content-type :object}

   "ACTIVITY_DELTA"
   {:required #{:type :message-id :activity-type :patch}
    :optional (opt base-optional attributable-optional)}

   "RAW"
   {:required #{:type :event}
    :optional (opt base-optional attributable-optional #{:source})}

   "CUSTOM"
   {:required #{:type :name :value}
    :optional (opt base-optional attributable-optional)}

   "RUN_STARTED"
   {:required #{:type :thread-id :run-id}
    :optional (opt base-optional #{:protocol-version :parent-run-id :input})}

   "RUN_FINISHED"
   {:required #{:type :thread-id :run-id}
    :optional (opt base-optional #{:result :outcome :usage})}

   "RUN_ERROR"
   {:required #{:type :message}
    :optional (opt base-optional #{:code :usage})}

   "STEP_STARTED"
   {:required #{:type :step-name}
    :optional (opt base-optional attributable-optional)}

   "STEP_FINISHED"
   {:required #{:type :step-name}
    :optional (opt base-optional attributable-optional)}

   "REASONING_START"
   {:required #{:type :message-id}
    :optional (opt base-optional attributable-optional)}

   "REASONING_MESSAGE_START"
   {:required #{:type :message-id :role}
    :optional (opt base-optional attributable-optional)
    :enums {:role #{"reasoning"}}}

   "REASONING_MESSAGE_CONTENT"
   {:required #{:type :message-id :delta}
    :optional (opt base-optional attributable-optional)}

   "REASONING_MESSAGE_END"
   {:required #{:type :message-id}
    :optional (opt base-optional attributable-optional)}

   "REASONING_MESSAGE_CHUNK"
   {:required #{:type}
    :optional (opt base-optional attributable-optional #{:message-id :delta})}

   "REASONING_END"
   {:required #{:type :message-id}
    :optional (opt base-optional attributable-optional)}

   "REASONING_ENCRYPTED_VALUE"
   {:required #{:type :subtype :entity-id :encrypted-value}
    :optional (opt base-optional attributable-optional)}

   "SUBAGENT_STARTED"
   {:required #{:type :subagent-run-id :name}
    :optional (opt base-optional #{:description :parent-subagent-run-id
                                   :parent-tool-call-id :parent-message-id})}

   "SUBAGENT_FINISHED"
   {:required #{:type :subagent-run-id}
    :optional base-optional}

   "SUBAGENT_ERROR"
   {:required #{:type :subagent-run-id :message}
    :optional base-optional}})

(def run-input-required #{:thread-id :run-id :messages})
(def run-input-optional #{:parent-run-id :protocol-version :state :tools
                          :context :forwarded-props :resume})
