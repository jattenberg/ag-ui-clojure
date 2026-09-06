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
  [events]
  (let [translated (compat/translate-stream events)
        events* (:events translated)
        structs (mapv #(validate/validate-event % {:mode :authoring}) events*)
        failed (filterv (comp not :ok) structs)]
    (if (seq failed)
      {:ok false :stage :structure :failures failed}
      (let [schema-results (mapv validate-against-schema events*)
            schema-fail (filterv (comp not :ok) schema-results)]
        (if (seq schema-fail)
          {:ok false :stage :schema :failures schema-fail}
          (let [expanded (chunks/expand-chunks events*)]
            (if-not (:ok expanded)
              {:ok false :stage :chunks :error (:error expanded)}
              (let [life (inv/check-stream (:events expanded))]
                (if-not (:ok life)
                  {:ok false :stage :lifecycle :violation (:violation life)}
                  (let [state (reduce/reduce-events events)]
                    (if (= :protocol-error (:status state))
                      {:ok false :stage :reduce :violation (:violation state)}
                      {:ok true
                       :events events*
                       :expanded (:events expanded)
                       :warnings (:warnings translated)
                       :state state})))))))))))

(defn- fixture-file [relative]
  (io/file (repo-root) relative))

(defn run-valid-fixtures
  []
  (let [manifest (load-manifest)
        entries (filterv #(= "valid" (:status %)) (:streams manifest))]
    (mapv (fn [entry]
            (let [f (fixture-file (:path entry))
                  loaded (load-event-file f)
                  decode-fail (filterv (comp not :ok) (:items loaded))]
              (if (seq decode-fail)
                {:name (:id entry)
                 :file (str f)
                 :ok false
                 :stage :decode
                 :failures decode-fail}
                (let [events (mapv :event (:items loaded))
                      result (check-valid-stream events)
                      trips (mapv round-trip (or (:events result) events))
                      trip-fail (filterv (comp not :ok) trips)]
                  (merge {:name (:id entry) :file (str f) :invariants (:invariants entry)}
                         (if (seq trip-fail)
                           {:ok false :stage :round-trip :failures trip-fail}
                           result))))))
          entries)))

(defn run-malformed-fixtures
  "Each malformed fixture must fail structure, decode, schema, or lifecycle."
  []
  (let [manifest (load-manifest)
        entries (filterv #(= "malformed" (:status %)) (:streams manifest))]
    (mapv (fn [entry]
            (let [f (fixture-file (:path entry))
                  loaded (load-event-file f)
                  decode-fail? (some (comp not :ok) (:items loaded))
                  events (mapv :event (filter :ok (:items loaded)))
                  checked (when (seq events) (check-valid-stream events))
                  rejected? (or decode-fail? (and checked (not (:ok checked))))]
              {:name (:id entry)
               :file (str f)
               :ok rejected?
               :expected :reject
               :why (:why entry)
               :decode-fail? (boolean decode-fail?)
               :check checked}))
          entries)))

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
