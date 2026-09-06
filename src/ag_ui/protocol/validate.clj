(ns ag-ui.protocol.validate
  "Structural validation. Lifecycle rules live in ag-ui.protocol.invariants.

  Modes:
    :authoring  — closed objects; unknown types and extra properties fail
                  (JSON Schema / fixture authoring).
    :runtime    — processing model: unknown types are not fatal; extra
                  properties are stripped with warnings; malformed known
                  values are fatal."
  (:require [ag-ui.protocol.events :as events]
            [ag-ui.protocol.schema :as schema]))

(defn- err [path code msg & [extra]]
  (merge {:path path :code code :message msg} extra))

(defn- type-of [v]
  (cond
    (nil? v) :nil
    (boolean? v) :boolean
    (integer? v) :integer
    (number? v) :number
    (string? v) :string
    (vector? v) :array
    (map? v) :object
    :else :other))

(defn- check-field-type
  [path k v spec]
  (cond
    (and (nil? v) (not (contains? schema/json-null-ok k)))
    [(err path :null-not-allowed
          (str "optional field " k " must be omitted, not null"))]

    (and (= k :delta) (:delta-is-patch spec))
    (if (vector? v)
      (mapcat (fn [i op]
                (if (map? op)
                  (let [op-name (or (:op op) (get op "op"))]
                    (cond
                      (nil? op-name)
                      [(err (conj path i) :invalid-field-type "JSON Patch operation missing op")]
                      (not (string? op-name))
                      [(err (conj path i :op) :invalid-field-type "op must be a string")]
                      :else []))
                  [(err (conj path i) :invalid-field-type "JSON Patch operations must be objects")]))
              (range) v)
      [(err path :invalid-field-type "STATE_DELTA.delta must be an array of JSON Patch operations")])

    (and (= k :content) (= :string (:content-type spec)))
    (if (string? v) [] [(err path :invalid-field-type "content must be a string")])

    (and (= k :content) (= :object (:content-type spec)))
    (if (map? v) [] [(err path :invalid-field-type "activity content must be an object")])

    (and (= k :timestamp) (integer? v)) []
    (and (= k :timestamp) (number? v))
    [(err path :invalid-field-type "timestamp must be an integer, not a float")]

    :else
    (let [expected (get schema/field-type k)]
      (cond
        (nil? expected) []
        (= expected :any) []
        (= expected :object) (if (map? v) [] [(err path :invalid-field-type (str k " must be an object"))])
        (= expected :array) (if (vector? v) [] [(err path :invalid-field-type (str k " must be an array"))])
        (= expected :boolean) (if (boolean? v) [] [(err path :invalid-field-type (str k " must be a boolean"))])
        (= expected :integer) (if (integer? v) [] [(err path :invalid-field-type (str k " must be an integer"))])
        (= expected :string) (if (string? v) [] [(err path :invalid-field-type (str k " must be a string"))])
        :else []))))

(defn- known-keys [spec]
  (into (:required spec) (:optional spec)))

(defn validate-event
  "Validate a Clojure event map. Returns {:ok true :event event :warnings [...]}
  or {:ok false :errors [...] :warnings [...]}."
  ([event] (validate-event event {:mode :authoring}))
  ([event {:keys [mode] :or {mode :authoring}}]
   (let [warnings (atom [])]
     (cond
       (not (map? event))
       {:ok false :errors [(err [] :not-object "event must be a JSON object")]}

       (nil? (:type event))
       {:ok false :errors [(err [:type] :missing-required "type is required")]}

       (not (string? (:type event)))
       {:ok false :errors [(err [:type] :invalid-field-type "type must be a string")]}

       (and (= mode :authoring)
            (not (contains? events/event-types (:type event))))
       {:ok false :errors [(err [:type] :unknown-event-type
                               (str "unknown event type " (:type event)))]}

       (and (= mode :runtime)
            (not (contains? events/event-types (:type event))))
       {:ok true
        :dropped? true
        :event event
        :warnings [(err [:type] :unrecognised-event
                        (str "unrecognised event type " (:type event) " dropped"))]}

       :else
       (let [t (:type event)
             spec (get schema/event-specs t)
             required (:required spec)
             allowed (known-keys spec)
             missing (into [] (remove #(contains? event %) (disj required :type)))
             extra (into [] (remove allowed (keys event)))
             type-errors (mapcat (fn [[k v]]
                                   (check-field-type [k] k v spec))
                                 event)
             enum-errors (mapcat (fn [[k allowed-vals]]
                                   (if (and (contains? event k)
                                            (not (contains? allowed-vals (get event k))))
                                     [(err [k] :invalid-enum
                                           (str k " must be one of " (pr-str allowed-vals)))]
                                     []))
                                 (:enums spec))
             missing-errors (mapv #(err [%] :missing-required (str % " is required")) missing)
             extra-errors (if (= mode :authoring)
                            (mapv #(err [%] :unevaluated-property
                                        (str "property " % " is not described on " t))
                                  extra)
                            [])
             stripped (if (= mode :runtime)
                        (apply dissoc event extra)
                        event)
             _ (when (and (= mode :runtime) (seq extra))
                 (swap! warnings concat
                        (map #(err [%] :stripped-property
                                   (str "unrecognised property " % " stripped"))
                             extra)))
             errors (into [] (concat missing-errors extra-errors type-errors enum-errors))]
         (if (seq errors)
           {:ok false :errors errors :warnings @warnings}
           {:ok true :event stripped :warnings @warnings}))))))

(defn valid-event?
  [event]
  (:ok (validate-event event)))

(defn validate-run-input
  ([input] (validate-run-input input {:mode :authoring}))
  ([input {:keys [mode] :or {mode :authoring}}]
   (cond
     (not (map? input))
     {:ok false :errors [(err [] :not-object "RunAgentInput must be an object")]}

     :else
     (let [missing (into [] (remove #(contains? input %) schema/run-input-required))
           allowed (into schema/run-input-required schema/run-input-optional)
           extra (into [] (remove allowed (keys input)))
           missing-errors (mapv #(err [%] :missing-required (str % " is required")) missing)
           extra-errors (if (= mode :authoring)
                          (mapv #(err [%] :unevaluated-property
                                      (str "property " % " is not described on RunAgentInput"))
                                extra)
                          [])
           type-errors (cond-> []
                         (and (contains? input :thread-id) (not (string? (:thread-id input))))
                         (conj (err [:thread-id] :invalid-field-type "threadId must be a string"))
                         (and (contains? input :run-id) (not (string? (:run-id input))))
                         (conj (err [:run-id] :invalid-field-type "runId must be a string"))
                         (and (contains? input :messages) (not (vector? (:messages input))))
                         (conj (err [:messages] :invalid-field-type "messages must be an array"))
                         (and (contains? input :tools) (not (vector? (:tools input))))
                         (conj (err [:tools] :invalid-field-type "tools must be an array"))
                         (and (contains? input :context) (not (vector? (:context input))))
                         (conj (err [:context] :invalid-field-type "context must be an array")))
           errors (into [] (concat missing-errors extra-errors type-errors))]
       (if (seq errors)
         {:ok false :errors errors}
         {:ok true :input (if (= mode :runtime) (apply dissoc input extra) input)
          :warnings (when (and (= mode :runtime) (seq extra))
                      (mapv #(err [%] :stripped-property (str "stripped " %)) extra))})))))
