(ns ag-ui.conformance.schema-jvm
  "JSON Schema 2020-12 checks against the pinned spec/draft/schema.json.
  JVM only — Babashka must not load this namespace."
  (:require [clojure.java.io :as io])
  (:import [com.fasterxml.jackson.databind JsonNode ObjectMapper]
           [com.networknt.schema JsonSchema JsonSchemaFactory SpecVersion$VersionFlag]))

(defonce ^ObjectMapper mapper (ObjectMapper.))

(defn- schema-file []
  (or (some-> (System/getenv "AG_UI_CLOJURE_ROOT") (io/file "spec" "draft" "schema.json"))
      (loop [dir (io/file (System/getProperty "user.dir"))]
        (when dir
          (let [f (io/file dir "spec" "draft" "schema.json")]
            (if (.isFile f)
              f
              (recur (.getParentFile dir))))))))

(defonce ^JsonSchema event-schema
  (let [factory (JsonSchemaFactory/getInstance SpecVersion$VersionFlag/V202012)
        node (.readTree mapper (schema-file))]
    (.getSchema factory ^JsonNode node)))

(defn validate-json
  "Validate a wire JSON string as an AG-UI Event."
  [json-string]
  (let [node (.readTree mapper ^String json-string)
        errors (.validate event-schema node)]
    (if (.isEmpty errors)
      {:ok true}
      {:ok false
       :errors (mapv #(.getMessage %) errors)})))
