(ns ag-ui.protocol.invariants
  "Executable stream invariants derived from the 1.0 draft behavioural spec.

  A structurally valid event can still violate sequencing. This namespace
  is the source of those checks."
  (:require [ag-ui.protocol.events :as events]
            [ag-ui.protocol.resume :as resume]))

(defn initial-tracker
  []
  {:phase :before-run
   :run-id nil
   :thread-id nil
   :open-messages #{}
   :open-tools #{}
   :open-steps #{}
   :open-reasoning #{}
   :open-reasoning-spans #{}
   :open-subagents #{}
   :subagent-parents {}
   :pending-interrupts []
   :message-roles {}
   :tool-names {}})

(defn- fail [tracker event code message]
  {:ok false
   :tracker tracker
   :violation {:code code
               :message message
               :event-type (:type event)
               :event event}})

(defn- ok [tracker]
  {:ok true :tracker tracker})

(defn- open-children
  [tracker parent-id]
  (into [] (keep (fn [[id parent]]
                   (when (= parent parent-id) id))
                 (:subagent-parents tracker))))

(defn- start-run [tracker event]
  (let [pending (:pending-interrupts tracker)
        input (:input event)
        resume-check (when (and (seq pending) (some? input))
                       (resume/check-resume pending (:resume input)))]
    (if (and resume-check (not (:ok resume-check)))
      (fail tracker event (:code resume-check) (:message resume-check))
      (ok (assoc (initial-tracker)
                 :phase :active
                 :run-id (:run-id event)
                 :thread-id (:thread-id event)
                 :pending-interrupts [])))))

(defn check-event
  "Apply one (already expanded) event to the lifecycle tracker."
  [tracker event]
  (let [t (:type event)
        phase (:phase tracker)]
    (cond
      (= phase :before-run)
      (cond
        (= t "RUN_STARTED")
        (start-run tracker event)
        (= t "RUN_ERROR")
        (ok (assoc tracker :phase :failed))
        :else
        (fail tracker event :stream-must-start
              "A stream MUST begin with RUN_STARTED or RUN_ERROR"))

      (= phase :failed)
      (if (= t "RUN_STARTED")
        (start-run tracker event)
        (fail tracker event :after-run-error
              "A producer MUST NOT emit anything after RUN_ERROR except RUN_STARTED"))

      (= phase :closed)
      (cond
        (= t "RUN_STARTED")
        (start-run tracker event)
        (= t "RUN_ERROR")
        (ok (assoc tracker :phase :failed))
        :else
        (fail tracker event :after-close
              "A consumer MUST reject any other event that arrives after a run has closed"))

      :else
      (let [sid (:subagent-run-id event)
            attr-err (when (and sid
                                (not (contains? events/run-scoped-types t))
                                (not (contains? events/subagent-identity-types t))
                                (seq (:open-subagents tracker))
                                (not (contains? (:open-subagents tracker) sid)))
                       (fail tracker event :unknown-subagent
                             "subagentRunId does not name an open subagent"))]
        (if attr-err
          attr-err
          (case t
            "RUN_STARTED"
            (fail tracker event :nested-run
                  "A RUN_STARTED while a run is still active is a violation")

            "RUN_FINISHED"
            (cond
              (seq (:open-messages tracker))
              (fail tracker event :open-at-finish
                    "Every item a producer opens MUST be closed before the run finishes (open text message)")
              (seq (:open-tools tracker))
              (fail tracker event :open-at-finish
                    "Every item a producer opens MUST be closed before the run finishes (open tool call)")
              (seq (:open-steps tracker))
              (fail tracker event :open-at-finish
                    "Every step a producer opens MUST be closed before the run finishes")
              (seq (:open-reasoning tracker))
              (fail tracker event :open-at-finish
                    "Every item a producer opens MUST be closed before the run finishes (open reasoning)")
              (seq (:open-reasoning-spans tracker))
              (fail tracker event :open-at-finish
                    "Every REASONING_START MUST be closed with REASONING_END before the run finishes")
              (seq (:open-subagents tracker))
              (fail tracker event :open-at-finish
                    "Every SUBAGENT_STARTED MUST be closed before the run finishes")
              (and (:run-id tracker) (:run-id event)
                   (not= (:run-id tracker) (:run-id event)))
              (fail tracker event :run-id-mismatch
                    "RUN_FINISHED.runId MUST agree with RUN_STARTED.runId")
              (and (:thread-id tracker) (:thread-id event)
                   (not= (:thread-id tracker) (:thread-id event)))
              (fail tracker event :thread-id-mismatch
                    "RUN_FINISHED.threadId MUST agree with RUN_STARTED.threadId")
              :else
              (ok (assoc tracker
                         :phase :closed
                         :pending-interrupts (or (resume/interrupts-from-finished event) []))))

            "RUN_ERROR"
            (ok (assoc tracker :phase :failed
                       :open-messages #{}
                       :open-tools #{}
                       :open-steps #{}
                       :open-reasoning #{}
                       :open-reasoning-spans #{}
                       :open-subagents #{}
                       :subagent-parents {}
                       :pending-interrupts []))

            "TEXT_MESSAGE_START"
            (if (contains? (:open-messages tracker) (:message-id event))
              (fail tracker event :already-open
                    "A producer MUST NOT open an item whose identifier is already open")
              (ok (-> tracker
                      (update :open-messages conj (:message-id event))
                      (assoc-in [:message-roles (:message-id event)]
                                (or (:role event) "assistant")))))

            "TEXT_MESSAGE_CONTENT"
            (if (contains? (:open-messages tracker) (:message-id event))
              (ok tracker)
              (fail tracker event :not-open
                    "A producer MUST NOT send a content event for an identifier that is not open"))

            "TEXT_MESSAGE_END"
            (if (contains? (:open-messages tracker) (:message-id event))
              (ok (update tracker :open-messages disj (:message-id event)))
              (fail tracker event :not-open
                    "A producer MUST NOT send an end event for an identifier that is not open"))

            "TOOL_CALL_START"
            (if (contains? (:open-tools tracker) (:tool-call-id event))
              (fail tracker event :already-open
                    "A producer MUST NOT open an item whose identifier is already open")
              (ok (-> tracker
                      (update :open-tools conj (:tool-call-id event))
                      (assoc-in [:tool-names (:tool-call-id event)] (:tool-call-name event)))))

            "TOOL_CALL_ARGS"
            (if (contains? (:open-tools tracker) (:tool-call-id event))
              (ok tracker)
              (fail tracker event :not-open
                    "A producer MUST NOT send a content event for an identifier that is not open"))

            "TOOL_CALL_END"
            (if (contains? (:open-tools tracker) (:tool-call-id event))
              (ok (update tracker :open-tools disj (:tool-call-id event)))
              (fail tracker event :not-open
                    "A producer MUST NOT send an end event for an identifier that is not open"))

            "STEP_STARTED"
            (if (contains? (:open-steps tracker) (:step-name event))
              (fail tracker event :already-open
                    "A producer MUST NOT open a step whose name is already open")
              (ok (update tracker :open-steps conj (:step-name event))))

            "STEP_FINISHED"
            (if (contains? (:open-steps tracker) (:step-name event))
              (ok (update tracker :open-steps disj (:step-name event)))
              (fail tracker event :not-open
                    "A producer MUST NOT finish a step that was never opened"))

            "REASONING_START"
            (if (contains? (:open-reasoning-spans tracker) (:message-id event))
              (fail tracker event :already-open
                    "A producer MUST NOT open a reasoning span whose messageId is already open")
              (ok (update tracker :open-reasoning-spans conj (:message-id event))))

            "REASONING_END"
            (if (contains? (:open-reasoning-spans tracker) (:message-id event))
              (ok (update tracker :open-reasoning-spans disj (:message-id event)))
              (fail tracker event :not-open
                    "A producer MUST NOT send REASONING_END for a span that is not open"))

            "REASONING_MESSAGE_START"
            (if (contains? (:open-reasoning tracker) (:message-id event))
              (fail tracker event :already-open
                    "A producer MUST NOT open an item whose identifier is already open")
              (ok (update tracker :open-reasoning conj (:message-id event))))

            "REASONING_MESSAGE_CONTENT"
            (if (contains? (:open-reasoning tracker) (:message-id event))
              (ok tracker)
              (fail tracker event :not-open
                    "A producer MUST NOT send a content event for an identifier that is not open"))

            "REASONING_MESSAGE_END"
            (if (contains? (:open-reasoning tracker) (:message-id event))
              (ok (update tracker :open-reasoning disj (:message-id event)))
              (fail tracker event :not-open
                    "A producer MUST NOT send an end event for an identifier that is not open"))

            "SUBAGENT_STARTED"
            (let [id (:subagent-run-id event)
                  parent (:parent-subagent-run-id event)]
              (cond
                (contains? (:open-subagents tracker) id)
                (fail tracker event :already-open
                      "A producer MUST NOT open a subagent whose subagentRunId is already open")
                (and parent (not (contains? (:open-subagents tracker) parent)))
                (fail tracker event :unknown-parent-subagent
                      "parentSubagentRunId must name an open subagent")
                :else
                (ok (-> tracker
                        (update :open-subagents conj id)
                        (assoc-in [:subagent-parents id] parent)))))

            "SUBAGENT_FINISHED"
            (let [id (:subagent-run-id event)
                  kids (open-children tracker id)]
              (cond
                (not (contains? (:open-subagents tracker) id))
                (fail tracker event :not-open
                      "A producer MUST NOT finish a subagent that was never opened")
                (seq kids)
                (fail tracker event :open-child-subagent
                      "A producer MUST NOT finish a subagent while nested invocations are still open")
                :else
                (ok (-> tracker
                        (update :open-subagents disj id)
                        (update :subagent-parents dissoc id)))))

            "SUBAGENT_ERROR"
            (let [id (:subagent-run-id event)
                  kids (open-children tracker id)]
              (cond
                (not (contains? (:open-subagents tracker) id))
                (fail tracker event :not-open
                      "A producer MUST NOT error a subagent that was never opened")
                (seq kids)
                (fail tracker event :open-child-subagent
                      "A producer MUST NOT error a subagent while nested invocations are still open")
                :else
                (ok (-> tracker
                        (update :open-subagents disj id)
                        (update :subagent-parents dissoc id)))))

            (ok tracker)))))))

(defn check-stream
  "Validate an expanded event sequence. Returns {:ok true :tracker} or
  {:ok false :violation :prefix-length}."
  [events]
  (loop [tracker (initial-tracker)
         xs events
         n 0]
    (if (empty? xs)
      {:ok true :tracker tracker}
      (let [{:keys [ok tracker] :as r} (check-event tracker (first xs))]
        (if ok
          (recur tracker (rest xs) (inc n))
          (assoc r :prefix-length n))))))
