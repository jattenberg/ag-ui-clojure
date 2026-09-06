(ns ag-ui.protocol.resume
  "Resume coverage for interrupt outcomes.

  Spec: a later RunAgentInput.resume must address every open interrupt
  from the interrupted run. Partial resumes are not supported.")

(defn interrupts-from-finished
  [event]
  (when (and (= "RUN_FINISHED" (:type event))
             (= "interrupt" (get-in event [:outcome :type])))
    (vec (get-in event [:outcome :interrupts]))))

(defn check-resume
  "Validate resume entries against open interrupts.
  Returns {:ok true} or {:ok false :code :message}."
  [open-interrupts resume-entries]
  (let [open-ids (into #{} (map :id) open-interrupts)
        resume (or resume-entries [])
        resume-ids (into #{} (map :interrupt-id) resume)
        missing (into [] (remove resume-ids) open-ids)
        extra (into [] (remove open-ids) resume-ids)]
    (cond
      (empty? open-ids)
      (if (seq resume)
        {:ok false
         :code :resume-without-interrupt
         :message "resume[] was sent but no interrupts are open"}
        {:ok true})

      (nil? resume-entries)
      {:ok false
       :code :resume-required
       :message "pending interrupts require RunAgentInput.resume covering every interrupt id"}

      (seq missing)
      {:ok false
       :code :partial-resume
       :message (str "resume[] missing interrupt ids " (pr-str missing))
       :missing missing}

      (seq extra)
      {:ok false
       :code :unknown-resume
       :message (str "resume[] references unknown interrupt ids " (pr-str extra))
       :extra extra}

      :else
      {:ok true})))
