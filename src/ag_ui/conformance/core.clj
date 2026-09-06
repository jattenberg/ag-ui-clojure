(ns ag-ui.conformance.core
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [ag-ui.serialization.json :as json]
            [ag-ui.protocol.validate :as validate]
            [ag-ui.protocol.chunks :as chunks]
            [ag-ui.protocol.invariants :as inv]
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

(defn check-valid-stream
  [events]
  (let [structs (mapv #(validate/validate-event % {:mode :authoring}) events)
        failed (filterv (comp not :ok) structs)]
    (if (seq failed)
      {:ok false :stage :structure :failures failed}
      (let [expanded (chunks/expand-chunks events)]
        (if-not (:ok expanded)
          {:ok false :stage :chunks :error (:error expanded)}
          (let [life (inv/check-stream (:events expanded))]
            (if-not (:ok life)
              {:ok false :stage :lifecycle :violation (:violation life)}
              (let [state (reduce/reduce-events events)]
                (if (= :protocol-error (:status state))
                  {:ok false :stage :reduce :violation (:violation state)}
                  {:ok true
                   :events events
                   :expanded (:events expanded)
                   :state state})))))))))

(defn round-trip
  [event]
  (let [encoded (json/encode-json event)
        decoded (json/decode-json-strict encoded)]
    {:encoded encoded
     :decoded decoded
     :ok (= decoded event)}))

(defn run-valid-fixtures
  []
  (let [dir (io/file (repo-root) "fixtures" "events")
        files (->> (.listFiles dir)
                   (filter #(.isFile %))
                   (sort-by #(.getName %)))]
    (mapv (fn [f]
            (let [loaded (load-event-file f)
                  decode-fail (filterv (comp not :ok) (:items loaded))]
              (if (seq decode-fail)
                {:name (.getName f)
                 :file (str f)
                 :ok false
                 :stage :decode
                 :failures decode-fail}
                (let [events (mapv :event (:items loaded))
                      result (check-valid-stream events)
                      trips (mapv round-trip events)
                      trip-fail (filterv (comp not :ok) trips)]
                  (merge {:name (.getName f) :file (str f)}
                         (if (seq trip-fail)
                           {:ok false :stage :round-trip :failures trip-fail}
                           result))))))
          files)))

(defn run-malformed-fixtures
  "Each malformed fixture must fail structure, decode, or lifecycle."
  []
  (let [dir (io/file (repo-root) "fixtures" "malformed")
        files (->> (.listFiles dir)
                   (filter #(.isFile %))
                   (filter #(re-find #"\.(json|jsonl)$" (.getName %)))
                   (sort-by #(.getName %)))]
    (mapv (fn [f]
            (let [loaded (load-event-file f)
                  decode-fail? (some (comp not :ok) (:items loaded))
                  events (mapv :event (filter :ok (:items loaded)))
                  checked (when (seq events) (check-valid-stream events))
                  rejected? (or decode-fail? (and checked (not (:ok checked))))]
              {:name (.getName f)
               :file (str f)
               :ok rejected?
               :expected :reject
               :decode-fail? (boolean decode-fail?)
               :check checked}))
          files)))

(defn check-endpoint
  [url]
  (client/run-agent
   {:url url
    :input {:thread-id "conformance"
            :run-id "conformance-run"
            :messages [{:id "u1" :role "user" :content "Hello"}]}}))
