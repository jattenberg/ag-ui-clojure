(ns ag-ui.state.reduce
  "Pure event reduction: protocol/application state from an event stream."
  (:require [ag-ui.protocol.validate :as validate]
            [ag-ui.protocol.invariants :as inv]
            [ag-ui.protocol.chunks :as chunks]
            [ag-ui.state.json-patch :as patch]
            [ag-ui.serialization.json :as json]))

(defn initial-state
  ([] (initial-state nil))
  ([run-input]
   {:status :idle
    :thread-id (:thread-id run-input)
    :run-id (:run-id run-input)
    :input run-input
    :messages (vec (:messages run-input))
    :messages-by-id (into {} (map (juxt :id identity) (:messages run-input)))
    :open-messages {}
    :open-tools {}
    :open-steps #{}
    :agent-state (json/stringify-keys (if (contains? run-input :state)
                                           (:state run-input)
                                           {}))
    :activities {}
    :custom []
    :raw []
    :outcome nil
    :result nil
    :error nil
    :usage nil
    :warnings []
    :tracker (inv/initial-tracker)}))

(defn- append-warning [state w]
  (update state :warnings (fnil conj []) w))

(defn- merge-meta [a b]
  (if (and (map? a) (map? b))
    (merge a b)
    (or b a)))

(defn- upsert-message [state message]
  (let [id (:id message)
        existing (get-in state [:messages-by-id id])]
    (if existing
      (let [idx (first (keep-indexed (fn [i m] (when (= id (:id m)) i)) (:messages state)))
            merged (merge existing message)]
        (-> state
            (assoc-in [:messages idx] merged)
            (assoc-in [:messages-by-id id] merged)))
      (-> state
          (update :messages (fnil conj []) message)
          (assoc-in [:messages-by-id id] message)))))

(defn- apply-messages-snapshot [state snapshot-messages]
  (let [snap-ids (set (map :id snapshot-messages))
        keep-roles #{"activity" "reasoning"}
        existing (:messages state)
        kept (filterv (fn [m]
                        (or (contains? snap-ids (:id m))
                            (and (contains? keep-roles (:role m))
                                 (not (some #(= (:role %) (:role m)) snapshot-messages)))))
                      existing)
        by-id (into {} (map (juxt :id identity) kept))
        replaced (reduce (fn [acc m]
                           (if (contains? by-id (:id m))
                             (let [idx (first (keep-indexed (fn [i x] (when (= (:id x) (:id m)) i)) acc))]
                               (assoc acc idx m))
                             (conj acc m)))
                         kept
                         snapshot-messages)]
    (assoc state
           :messages replaced
           :messages-by-id (into {} (map (juxt :id identity) replaced)))))

(defn reduce-event
  "Apply one expanded, structurally valid event. Returns a new state map.
  Protocol violations set :status to :protocol-error and record :violation."
  [state event]
  (let [v (validate/validate-event event {:mode :runtime})]
    (cond
      (not (:ok v))
      (assoc state :status :protocol-error :violation {:structural (:errors v) :event event})

      (:dropped? v)
      (append-warning state (first (:warnings v)))

      :else
      (let [event (:event v)
            checked (inv/check-event (:tracker state) event)]
        (if-not (:ok checked)
          (assoc state
                 :status :protocol-error
                 :violation (:violation checked)
                 :tracker (:tracker checked))
          (let [state (cond-> (assoc state :tracker (:tracker checked))
                        (seq (:warnings v)) (update :warnings into (:warnings v)))
                t (:type event)]
            (case t
              "RUN_STARTED"
              (assoc state
                     :status :active
                     :thread-id (:thread-id event)
                     :run-id (:run-id event)
                     :protocol-version (:protocol-version event)
                     :outcome nil
                     :result nil
                     :error nil)

              "RUN_FINISHED"
              (assoc state
                     :status :finished
                     :outcome (or (:outcome event) {:type "success"})
                     :result (:result event)
                     :usage (:usage event)
                     :interrupts (get-in event [:outcome :interrupts]))

              "RUN_ERROR"
              (assoc state
                     :status :error
                     :error {:message (:message event) :code (:code event)}
                     :usage (:usage event)
                     :open-messages {}
                     :open-tools {})

              "TEXT_MESSAGE_START"
              (let [msg {:id (:message-id event)
                         :role (or (:role event) "assistant")
                         :content ""
                         :name (:name event)
                         :metadata (:metadata event)}]
                (-> state
                    (assoc-in [:open-messages (:message-id event)] msg)
                    (upsert-message msg)))

              "TEXT_MESSAGE_CONTENT"
              (let [id (:message-id event)
                    open (get-in state [:open-messages id])
                    msg (-> open
                            (update :content str (:delta event))
                            (update :metadata merge-meta (:metadata event)))]
                (-> state
                    (assoc-in [:open-messages id] msg)
                    (upsert-message msg)))

              "TEXT_MESSAGE_END"
              (let [id (:message-id event)
                    open (get-in state [:open-messages id])
                    msg (update open :metadata merge-meta (:metadata event))]
                (-> state
                    (update :open-messages dissoc id)
                    (upsert-message msg)))

              "TOOL_CALL_START"
              (let [tc {:id (:tool-call-id event)
                        :type "function"
                        :function {:name (:tool-call-name event) :arguments ""}}
                    parent (:parent-message-id event)
                    msg (or (get-in state [:messages-by-id parent])
                            (and parent {:id parent :role "assistant" :content "" :tool-calls []}))
                    msg (when msg (update msg :tool-calls (fnil conj []) tc))]
                (cond-> (assoc-in state [:open-tools (:tool-call-id event)]
                                  (assoc tc :parent-message-id parent))
                  msg (upsert-message msg)))

              "TOOL_CALL_ARGS"
              (let [id (:tool-call-id event)
                    open (get-in state [:open-tools id])
                    args (str (get-in open [:function :arguments]) (:delta event))
                    open (assoc-in open [:function :arguments] args)
                    parent (:parent-message-id open)
                    msg (get-in state [:messages-by-id parent])
                    msg (when msg
                          (update msg :tool-calls
                                  (fn [tcs]
                                    (mapv (fn [tc]
                                            (if (= (:id tc) id)
                                              (assoc-in tc [:function :arguments] args)
                                              tc))
                                          tcs))))]
                (cond-> (assoc-in state [:open-tools id] open)
                  msg (upsert-message msg)))

              "TOOL_CALL_END"
              (update state :open-tools dissoc (:tool-call-id event))

              "TOOL_CALL_RESULT"
              (upsert-message state {:id (:message-id event)
                                     :role "tool"
                                     :tool-call-id (:tool-call-id event)
                                     :content (:content event)
                                     :error (:error event)})

              "STATE_SNAPSHOT"
              (assoc state :agent-state (json/stringify-keys (:snapshot event)))

              "STATE_DELTA"
              (let [{:keys [ok doc reason]} (patch/apply-patch (:agent-state state) (:delta event))]
                (if ok
                  (assoc state :agent-state doc)
                  (append-warning state {:code :patch-failed :message reason :event event})))

              "MESSAGES_SNAPSHOT"
              (apply-messages-snapshot state (:messages event))

              "ACTIVITY_SNAPSHOT"
              (assoc-in state [:activities (:message-id event)]
                        {:id (:message-id event)
                         :role "activity"
                         :activity-type (:activity-type event)
                         :content (json/stringify-keys (:content event))})

              "ACTIVITY_DELTA"
              (let [id (:message-id event)
                    current (get-in state [:activities id :content] {})
                    current (json/stringify-keys current)
                    {:keys [ok doc reason]} (patch/apply-patch current (:patch event))]
                (if ok
                  (assoc-in state [:activities id :content] doc)
                  (append-warning state {:code :patch-failed :message reason :event event})))

              "CUSTOM"
              (update state :custom conj event)

              "RAW"
              (update state :raw conj event)

              "STEP_STARTED"
              (update state :open-steps conj (:step-name event))

              "STEP_FINISHED"
              (update state :open-steps disj (:step-name event))

              ;; remaining feature events: record only
              (update state :unhandled (fnil conj []) event))))))))

(defn reduce-events
  "Expand chunks, then fold events. run-input seeds messages/state."
  ([events] (reduce-events events nil))
  ([events run-input]
   (let [expanded (chunks/expand-chunks events)]
     (if-not (:ok expanded)
       {:status :protocol-error :violation (:error expanded)}
       (reduce reduce-event (initial-state run-input) (:events expanded))))))
