(ns ag-ui.server.echo
  "Deterministic fake agent. No LLM. Behaviour is a function of RunAgentInput."
  (:require [clojure.string :as str]))

(defn- last-user-text [input]
  (let [msg (->> (:messages input)
                 reverse
                 (filter #(= "user" (:role %)))
                 first)
        c (:content msg)]
    (cond
      (string? c) c
      (vector? c) (->> c (filter #(= "text" (:type %))) (map :text) (str/join ""))
      :else "")))

(defn- text-run [thread-id run-id message-id chunks]
  (concat
   [{:type "RUN_STARTED" :thread-id thread-id :run-id run-id :protocol-version "1.0"}
    {:type "TEXT_MESSAGE_START" :message-id message-id :role "assistant"}]
   (map (fn [d] {:type "TEXT_MESSAGE_CONTENT" :message-id message-id :delta d}) chunks)
   [{:type "TEXT_MESSAGE_END" :message-id message-id}
    {:type "RUN_FINISHED" :thread-id thread-id :run-id run-id}]))

(defn events-for
  "Pure: input map -> event vector."
  [input]
  (let [thread-id (or (:thread-id input) "thread_missing")
        run-id (or (:run-id input) "run_missing")
        text (last-user-text input)
        lower (str/lower-case (or text ""))]
    (cond
      (seq (:resume input))
      [{:type "RUN_STARTED" :thread-id thread-id :run-id run-id :protocol-version "1.0"
        :input input}
       {:type "TEXT_MESSAGE_START" :message-id "msg_1" :role "assistant"}
       {:type "TEXT_MESSAGE_CONTENT" :message-id "msg_1" :delta "Resumed."}
       {:type "TEXT_MESSAGE_END" :message-id "msg_1"}
       {:type "RUN_FINISHED" :thread-id thread-id :run-id run-id
        :outcome {:type "success"}}]

      (str/includes? lower "tool")
      [{:type "RUN_STARTED" :thread-id thread-id :run-id run-id :protocol-version "1.0"}
       {:type "TOOL_CALL_START" :tool-call-id "call_1" :tool-call-name "lookup" :parent-message-id "msg_tool"}
       {:type "TOOL_CALL_ARGS" :tool-call-id "call_1" :delta "{\"q\":"}
       {:type "TOOL_CALL_ARGS" :tool-call-id "call_1" :delta "\"clojure\"}"}
       {:type "TOOL_CALL_END" :tool-call-id "call_1"}
       {:type "TOOL_CALL_RESULT" :message-id "msg_result" :tool-call-id "call_1" :content "ok"}
       {:type "RUN_FINISHED" :thread-id thread-id :run-id run-id}]

      (str/includes? lower "state")
      [{:type "RUN_STARTED" :thread-id thread-id :run-id run-id :protocol-version "1.0"}
       {:type "STATE_SNAPSHOT" :snapshot {:count 0 :label "n"}}
       {:type "STATE_DELTA" :delta [{:op "replace" :path "/count" :value 1}]}
       {:type "RUN_FINISHED" :thread-id thread-id :run-id run-id}]

      (str/includes? lower "interrupt")
      [{:type "RUN_STARTED" :thread-id thread-id :run-id run-id :protocol-version "1.0"}
       {:type "STATE_SNAPSHOT" :snapshot {:awaiting true}}
       {:type "MESSAGES_SNAPSHOT" :messages (vec (:messages input))}
       {:type "RUN_FINISHED"
        :thread-id thread-id
        :run-id run-id
        :outcome {:type "interrupt"
                  :interrupts [{:id "int_1"
                                :reason "confirmation"
                                :message "Proceed?"}]}}]

      (str/includes? lower "error")
      [{:type "RUN_STARTED" :thread-id thread-id :run-id run-id :protocol-version "1.0"}
       {:type "RUN_ERROR" :message "deterministic failure" :code "echo.error"}]

      (str/includes? lower "custom")
      [{:type "RUN_STARTED" :thread-id thread-id :run-id run-id :protocol-version "1.0"}
       {:type "CUSTOM" :name "ag-ui-clojure.ping" :value {:ok true}}
       {:type "RUN_FINISHED" :thread-id thread-id :run-id run-id}]

      (str/includes? lower "activity")
      [{:type "RUN_STARTED" :thread-id thread-id :run-id run-id :protocol-version "1.0"}
       {:type "ACTIVITY_SNAPSHOT" :message-id "act_1" :activity-type "progress" :content {"pct" 0}}
       {:type "ACTIVITY_DELTA" :message-id "act_1" :activity-type "progress"
        :patch [{:op "replace" :path "/pct" :value 100}]}
       {:type "RUN_FINISHED" :thread-id thread-id :run-id run-id}]

      (str/includes? lower "subagent")
      [{:type "RUN_STARTED" :thread-id thread-id :run-id run-id :protocol-version "1.0"}
       {:type "SUBAGENT_STARTED" :subagent-run-id "sa_1" :name "researcher"}
       {:type "SUBAGENT_STARTED" :subagent-run-id "sa_2" :name "writer" :parent-subagent-run-id "sa_1"}
       {:type "TEXT_MESSAGE_START" :message-id "msg_sa" :role "assistant" :subagent-run-id "sa_2"}
       {:type "TEXT_MESSAGE_CONTENT" :message-id "msg_sa" :delta "nested" :subagent-run-id "sa_2"}
       {:type "TEXT_MESSAGE_END" :message-id "msg_sa" :subagent-run-id "sa_2"}
       {:type "SUBAGENT_FINISHED" :subagent-run-id "sa_2"}
       {:type "SUBAGENT_FINISHED" :subagent-run-id "sa_1"}
       {:type "RUN_FINISHED" :thread-id thread-id :run-id run-id}]

      (str/includes? lower "encrypted")
      [{:type "RUN_STARTED" :thread-id thread-id :run-id run-id :protocol-version "1.0"}
       {:type "REASONING_START" :message-id "span_1"}
       {:type "REASONING_ENCRYPTED_VALUE" :subtype "message" :entity-id "span_1"
        :encrypted-value "opaque"}
       {:type "REASONING_END" :message-id "span_1"}
       {:type "RUN_FINISHED" :thread-id thread-id :run-id run-id}]

      (str/includes? lower "reasoning")
      [{:type "RUN_STARTED" :thread-id thread-id :run-id run-id :protocol-version "1.0"}
       {:type "REASONING_START" :message-id "span_1"}
       {:type "REASONING_MESSAGE_START" :message-id "rm_1" :role "reasoning"}
       {:type "REASONING_MESSAGE_CONTENT" :message-id "rm_1" :delta "thinking"}
       {:type "REASONING_MESSAGE_END" :message-id "rm_1"}
       {:type "REASONING_END" :message-id "span_1"}
       {:type "RUN_FINISHED" :thread-id thread-id :run-id run-id}]

      (str/includes? lower "snapshot")
      [{:type "RUN_STARTED" :thread-id thread-id :run-id run-id :protocol-version "1.0"}
       {:type "ACTIVITY_SNAPSHOT" :message-id "act_keep" :activity-type "progress" :content {"pct" 1}}
       {:type "TEXT_MESSAGE_START" :message-id "drop_me" :role "assistant"}
       {:type "TEXT_MESSAGE_CONTENT" :message-id "drop_me" :delta "gone"}
       {:type "TEXT_MESSAGE_END" :message-id "drop_me"}
       {:type "MESSAGES_SNAPSHOT"
        :messages [{:id "u1" :role "user" :content "hi"}
                   {:id "a1" :role "assistant" :content "there"}]}
       {:type "RUN_FINISHED" :thread-id thread-id :run-id run-id}]

      (str/includes? lower "chunk")
      [{:type "RUN_STARTED" :thread-id thread-id :run-id run-id :protocol-version "1.0"}
       {:type "TEXT_MESSAGE_CHUNK" :message-id "msg_1" :role "assistant" :delta "Hello"}
       {:type "TEXT_MESSAGE_CHUNK" :delta ", chunks."}
       {:type "RUN_FINISHED" :thread-id thread-id :run-id run-id}]

      :else
      (text-run thread-id run-id "msg_1" ["Hello" ", world."]))))
