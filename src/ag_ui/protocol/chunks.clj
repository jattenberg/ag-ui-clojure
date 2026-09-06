(ns ag-ui.protocol.chunks
  "Expand TEXT_MESSAGE_CHUNK / TOOL_CALL_CHUNK / REASONING_MESSAGE_CHUNK
  into start/content/end events before verification.

  See spec/draft/basic/patterns/streaming.md.")

(def ^:private lane-closers
  #{"TEXT_MESSAGE_START" "TEXT_MESSAGE_CONTENT" "TEXT_MESSAGE_END"
    "TOOL_CALL_START" "TOOL_CALL_ARGS" "TOOL_CALL_END" "TOOL_CALL_RESULT"
    "STEP_STARTED" "STEP_FINISHED"
    "STATE_SNAPSHOT" "STATE_DELTA" "CUSTOM"
    "REASONING_START" "REASONING_END"
    "REASONING_MESSAGE_START" "REASONING_MESSAGE_CONTENT" "REASONING_MESSAGE_END"})

(def ^:private run-level-closers
  #{"RUN_STARTED" "RUN_FINISHED" "RUN_ERROR" "MESSAGES_SNAPSHOT"
    "SUBAGENT_FINISHED" "SUBAGENT_ERROR"})

(defn- flush-open [state]
  (concat
   (when-let [open (:text state)]
     [{:type "TEXT_MESSAGE_END" :message-id (:message-id open)}])
   (when-let [open (:tool state)]
     [{:type "TOOL_CALL_END" :tool-call-id (:tool-call-id open)}])
   (when-let [open (:reason state)]
     [{:type "REASONING_MESSAGE_END" :message-id (:message-id open)}])))

(defn- anything-open? [state]
  (or (:text state) (:tool state) (:reason state)))

(defn expand-chunks
  "Return {:ok true :events [...]} or {:ok false :error ...}."
  [events]
  (loop [remaining (vec events)
         i 0
         state {:text nil :tool nil :reason nil}
         out []]
    (if (= i (count remaining))
      {:ok true :events (into out (flush-open state))}
      (let [e (nth remaining i)
            t (:type e)]
        (cond
          (= t "TEXT_MESSAGE_CHUNK")
          (let [open (:text state)
                id (:message-id e)
                role (or (:role e) "assistant")]
            (cond
              (nil? open)
              (if-not id
                {:ok false :error {:code :chunk-missing-id
                                   :message "first TEXT_MESSAGE_CHUNK must carry messageId"
                                   :event e}}
                (let [start (cond-> {:type "TEXT_MESSAGE_START"
                                     :message-id id
                                     :role role}
                              (contains? e :name) (assoc :name (:name e)))]
                  (recur remaining (inc i)
                         (assoc state :text {:message-id id :role role :name (:name e)})
                         (into out [start {:type "TEXT_MESSAGE_CONTENT"
                                           :message-id id
                                           :delta (or (:delta e) "")}]))))

              (and id (not= id (:message-id open)))
              (recur remaining i (assoc state :text nil) (into out (flush-open {:text open})))

              (and (contains? e :role) (not= (:role e) (:role open)))
              {:ok false :error {:code :chunk-conflict
                                 :message "TEXT_MESSAGE_CHUNK role conflicts with opener"
                                 :event e}}

              :else
              (recur remaining (inc i) state
                     (conj out {:type "TEXT_MESSAGE_CONTENT"
                                :message-id (:message-id open)
                                :delta (or (:delta e) "")}))))

          (= t "TOOL_CALL_CHUNK")
          (let [open (:tool state)
                id (:tool-call-id e)
                name (:tool-call-name e)]
            (cond
              (nil? open)
              (if (or (nil? id) (nil? name))
                {:ok false :error {:code :chunk-missing-id
                                   :message "first TOOL_CALL_CHUNK must carry toolCallId and toolCallName"
                                   :event e}}
                (let [start (cond-> {:type "TOOL_CALL_START"
                                     :tool-call-id id
                                     :tool-call-name name}
                              (contains? e :parent-message-id)
                              (assoc :parent-message-id (:parent-message-id e)))]
                  (recur remaining (inc i)
                         (assoc state :tool {:tool-call-id id
                                             :tool-call-name name
                                             :parent-message-id (:parent-message-id e)})
                         (into out [start {:type "TOOL_CALL_ARGS"
                                           :tool-call-id id
                                           :delta (or (:delta e) "")}]))))

              (and id (not= id (:tool-call-id open)))
              (recur remaining i (assoc state :tool nil) (into out (flush-open {:tool open})))

              (or (and (contains? e :tool-call-name)
                       (not= (:tool-call-name e) (:tool-call-name open)))
                  (and (contains? e :parent-message-id)
                       (not= (:parent-message-id e) (:parent-message-id open))))
              {:ok false :error {:code :chunk-conflict
                                 :message "TOOL_CALL_CHUNK opener fields conflict"
                                 :event e}}

              :else
              (recur remaining (inc i) state
                     (conj out {:type "TOOL_CALL_ARGS"
                                :tool-call-id (:tool-call-id open)
                                :delta (or (:delta e) "")}))))

          (= t "REASONING_MESSAGE_CHUNK")
          (let [open (:reason state)
                id (:message-id e)]
            (cond
              (nil? open)
              (if-not id
                {:ok false :error {:code :chunk-missing-id
                                   :message "first REASONING_MESSAGE_CHUNK must carry messageId"
                                   :event e}}
                (recur remaining (inc i)
                       (assoc state :reason {:message-id id})
                       (into out [{:type "REASONING_MESSAGE_START"
                                   :message-id id
                                   :role "reasoning"}
                                  {:type "REASONING_MESSAGE_CONTENT"
                                   :message-id id
                                   :delta (or (:delta e) "")}])))

              (and id (not= id (:message-id open)))
              (recur remaining i (assoc state :reason nil) (into out (flush-open {:reason open})))

              :else
              (recur remaining (inc i) state
                     (conj out {:type "REASONING_MESSAGE_CONTENT"
                                :message-id (:message-id open)
                                :delta (or (:delta e) "")}))))

          (and (anything-open? state)
               (or (contains? run-level-closers t)
                   (contains? lane-closers t)))
          (recur remaining i {:text nil :tool nil :reason nil}
                 (into out (flush-open state)))

          :else
          (recur remaining (inc i) state (conj out e)))))))
