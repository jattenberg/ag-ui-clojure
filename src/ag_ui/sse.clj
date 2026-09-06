(ns ag-ui.sse
  "SSE framing for AG-UI: one protocol event per `data:` payload.

  Binding rules from spec/draft/basic/transports/http-sse.md:
    - LF line endings
    - ignore event:, id:, retry:
    - tolerate comment lines
    - multi-line data fields join with newline"
  (:require [clojure.string :as str]
            [ag-ui.serialization.json :as json]
            [ag-ui.protocol.validate :as validate]))

(defn encode-event
  "Frame one Clojure event as an SSE record ending in \\n\\n."
  [event]
  (str "data: " (json/encode-json event) "\n\n"))

(defn encode-stream
  [events]
  (apply str (map encode-event events)))

(defn- parse-block [block]
  (let [data-lines (->> (str/split-lines block)
                        (remove #(str/starts-with? % ":"))
                        (keep (fn [line]
                                (cond
                                  (str/starts-with? line "data:")
                                  (let [rest (subs line 5)]
                                    (if (str/starts-with? rest " ")
                                      (subs rest 1)
                                      rest))
                                  (or (str/starts-with? line "event:")
                                      (str/starts-with? line "id:")
                                      (str/starts-with? line "retry:"))
                                  nil
                                  :else nil))))]
    (when (seq data-lines)
      (str/join "\n" data-lines))))

(defn decode-stream
  "Parse an SSE body into a vector of {:ok true :event ...} or
  {:ok false :error ... :raw ...} maps, preserving order.

  Does not drop malformed events."
  [body]
  (if (or (nil? body) (str/blank? body))
    []
    (let [blocks (->> (str/split body #"\n\n")
                      (remove str/blank?))]
      (mapv (fn [block]
              (let [payload (parse-block block)]
                (if (nil? payload)
                  {:ok false :kind :empty-sse :raw block}
                  (try
                    (let [decoded (json/decode-json-strict payload)
                          v (validate/validate-event decoded {:mode :runtime})]
                      (if (:ok v)
                        (if (:dropped? v)
                          {:ok true :dropped? true :event (:event v) :warnings (:warnings v) :raw payload}
                          {:ok true :event (:event v) :warnings (:warnings v) :raw payload})
                        {:ok false :kind :invalid-event :errors (:errors v) :raw payload}))
                    (catch Exception e
                      {:ok false :kind :malformed-json
                       :error (.getMessage e)
                       :raw payload})))))
            blocks))))

(defn events-only
  "Extract successfully decoded events, throwing on the first transport/protocol error."
  [decoded]
  (mapv (fn [item]
          (when-not (:ok item)
            (throw (ex-info "SSE stream contained a malformed event"
                            {:item item})))
          (:event item))
        (remove :dropped? decoded)))
