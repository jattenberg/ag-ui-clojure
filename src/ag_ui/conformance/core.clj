(ns ag-ui.conformance.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [ag-ui.serialization.json :as json]
            [ag-ui.protocol.validate :as validate]
            [ag-ui.protocol.chunks :as chunks]
            [ag-ui.protocol.invariants :as inv]
            [ag-ui.protocol.compat :as compat]
            [ag-ui.protocol.resume :as resume]
            [ag-ui.state.reduce :as reduce]
            [ag-ui.client.http :as client]))

(defn repo-root
  []
  (or (some-> (System/getenv "AG_UI_CLOJURE_ROOT") io/file)
      (loop [dir (io/file (System/getProperty "user.dir"))]
        (cond
          (nil? dir) (io/file ".")
          (.isDirectory (io/file dir "fixtures")) dir
          :else (recur (.getParentFile dir))))))

(defn read-jsonl
  [file]
  (with-open [r (io/reader file)]
    (vec (for [line (line-seq r)
               :when (not (str/blank? line))
               :when (not (str/starts-with? (str/trim line) "#"))]
           line))))

(defn load-event-file
  "Load a .json object or .jsonl stream. Returns {:ok ...} or decode errors."
  [file]
  (let [file (io/file file)
        name (.getName file)]
    (if (str/ends-with? name ".jsonl")
      (let [lines (read-jsonl file)
            parsed (mapv (fn [line]
                           (try
                             {:ok true :event (json/decode-json-strict line) :raw line}
                             (catch Exception e
                               {:ok false :kind :malformed-json :error (.getMessage e) :raw line})))
                         lines)]
        {:kind :stream :items parsed :file file})
      (try
        {:kind :object
         :items [{:ok true :event (json/decode-json-strict (slurp file))}]
         :file file}
        (catch Exception e
          {:kind :object
           :items [{:ok false :kind :malformed-json :error (.getMessage e)}]
           :file file})))))

(defn babashka?
  []
  (some? (System/getProperty "babashka.version")))

(defn validate-against-schema
  "Authoring check against spec/draft/schema.json. JVM-only; skipped on Babashka."
  [event]
  (if (babashka?)
    {:ok true :skipped true}
    (try
      (let [validate (requiring-resolve 'ag-ui.conformance.schema-jvm/validate-json)]
        (validate (json/encode-json event)))
      (catch Throwable t
        {:ok false :error (.getMessage t)}))))

(defn load-manifest
  []
  (json/decode-json (slurp (io/file (repo-root) "fixtures" "manifest.json"))))

(defn round-trip
  [event]
  (let [encoded (json/encode-json event)
        decoded (json/decode-json-strict encoded)]
    {:encoded encoded
     :decoded decoded
     :ok (= decoded event)}))

(defn check-valid-stream
  "Validate a decoded event seq. profile is :producer (authoring+schema) or :consumer (runtime)."
  ([events] (check-valid-stream events :producer))
  ([events profile]
   (let [translated (compat/translate-stream events)
         events* (:events translated)
         mode (if (= profile :consumer) :runtime :authoring)
         structs (mapv #(validate/validate-event % {:mode mode}) events*)
         fatal (filterv (fn [r] (and (not (:ok r)) (not (:dropped? r)))) structs)
         kept (if (= profile :consumer)
                (into [] (keep (fn [r]
                                 (when (and (:ok r) (not (:dropped? r)))
                                   (:event r)))
                               structs))
                events*)
         schema-fail (when (= profile :producer)
                       (filterv (comp not :ok) (mapv validate-against-schema kept)))]
     (cond
       (seq fatal)
       {:ok false :stage :structure :failures fatal :warnings (:warnings translated)}

       (seq schema-fail)
       {:ok false :stage :schema :failures schema-fail}

       :else
       (let [expanded (chunks/expand-chunks kept)]
         (if-not (:ok expanded)
           {:ok false :stage :chunks :error (:error expanded)}
           (let [life (inv/check-stream (:events expanded))]
             (if-not (:ok life)
               {:ok false :stage :lifecycle :violation (:violation life)}
               (let [state (reduce/reduce-events events)]
                 (if (= :protocol-error (:status state))
                   {:ok false :stage :reduce :violation (:violation state)}
                   {:ok true
                    :events kept
                    :expanded (:events expanded)
                    :warnings (into (vec (:warnings translated))
                                    (mapcat :warnings structs))
                    :state state}))))))))))

(defn- fixture-file [relative]
  (io/file (repo-root) relative))

(defn included-in-profile?
  [entry profile]
  (let [p (name profile)
        override (get entry (keyword p))]
    (cond
      (some? override) true
      (seq (:profiles entry)) (contains? (set (:profiles entry)) p)
      :else true)))

(defn expected-outcome
  "accept | reject, from per-profile override or status."
  [entry profile]
  (or (get entry (keyword (name profile)))
      (if (= "valid" (:status entry)) "accept" "reject")))

(defn- evaluate-entry
  [entry profile]
  (if-not (included-in-profile? entry profile)
    {:name (:id entry) :ok true :skipped true :profile profile}
    (let [expect (expected-outcome entry profile)
          f (fixture-file (:path entry))
          loaded (load-event-file f)
          decode-fail (filterv (comp not :ok) (:items loaded))
          events (mapv :event (filter :ok (:items loaded)))
          checked (when (seq events) (check-valid-stream events profile))
          accepted? (and (empty? decode-fail) (boolean (:ok checked)))
          met? (if (= "accept" expect) accepted? (not accepted?))
          trips (when (and met? (= "accept" expect) (seq (:events checked)))
                  (mapv round-trip (:events checked)))
          trip-fail (filterv (comp not :ok) (or trips []))]
      (merge {:name (:id entry)
              :file (str f)
              :profile profile
              :expected expect
              :why (:why entry)
              :invariants (:invariants entry)
              :decode-fail? (boolean (seq decode-fail))
              :check checked}
             (if (seq trip-fail)
               {:ok false :stage :round-trip :failures trip-fail}
               {:ok met?})))))

(defn run-profile
  "Run every manifest stream against one profile. :ok means the expectation was met."
  [profile]
  (mapv #(evaluate-entry % profile) (:streams (load-manifest))))

(defn run-valid-fixtures
  []
  (filterv (fn [r]
             (and (not (:skipped r))
                  (= "accept" (:expected r))))
           (run-profile :producer)))

(defn run-malformed-fixtures
  "Each malformed fixture must fail structure, decode, schema, or lifecycle."
  []
  (filterv (fn [r]
             (and (not (:skipped r))
                  (= "reject" (:expected r))))
           (run-profile :producer)))

(defn run-resume-fixtures
  []
  (let [dir (io/file (repo-root) "fixtures" "runs")
        files (->> (or (.listFiles dir) (into-array java.io.File []))
                   (filter #(str/ends-with? (.getName %) ".json"))
                   (sort-by #(.getName %)))]
    (mapv (fn [f]
            (let [doc (json/decode-json-strict (slurp f))]
              (if-not (:expect-resume-check doc)
                {:name (.getName f) :ok true :skipped true}
                (let [open (:open-interrupts doc)
                      resume (get-in doc [:input :resume])
                      result (resume/check-resume open resume)
                      expect-ok (:expect-ok doc)]
                  {:name (.getName f)
                   :file (str f)
                   :ok (= (boolean (:ok result)) (boolean expect-ok))
                   :result result}))))
          files)))

(defn check-endpoint
  [url]
  (client/run-agent
   {:url url
    :input {:thread-id "conformance"
            :run-id "conformance-run"
            :messages [{:id "u1" :role "user" :content "Hello"}]}}))
