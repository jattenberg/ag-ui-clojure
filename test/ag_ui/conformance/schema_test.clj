(ns ag-ui.conformance.schema-test
  (:require [clojure.test :refer [deftest is]]
            [ag-ui.serialization.json :as json]
            [ag-ui.conformance.schema-jvm :as schema]))

(deftest schema-accepts-run-started
  (is (:ok (schema/validate-json
            (json/encode-json {:type "RUN_STARTED" :thread-id "t" :run-id "r"})))))

(deftest schema-rejects-unknown-type
  (is (not (:ok (schema/validate-json "{\"type\":\"NOT_A_REAL_EVENT\"}")))))
