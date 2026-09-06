(ns ag-ui.conformance.fixtures-test
  (:require [clojure.test :refer [deftest is]]
            [ag-ui.conformance.core :as core]))

(deftest valid-fixtures-pass
  (doseq [r (core/run-valid-fixtures)]
    (is (:ok r) (pr-str r))))

(deftest malformed-fixtures-fail
  (doseq [r (core/run-malformed-fixtures)]
    (is (:ok r) (str "should reject " (:name r) " " (pr-str r)))))

(deftest resume-fixtures
  (doseq [r (core/run-resume-fixtures)]
    (is (:ok r) (pr-str r))))

(deftest producer-and-consumer-profiles
  (doseq [profile [:producer :consumer]]
    (doseq [r (core/run-profile profile)]
      (is (:ok r) (pr-str r)))))
