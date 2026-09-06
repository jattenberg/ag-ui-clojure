(ns ag-ui.bb-test
  "Babashka unit-test runner. Property tests stay on the JVM."
  (:require [clojure.test :as t]))

(def unit-namespaces
  '[ag-ui.protocol.validate-test
    ag-ui.protocol.invariants-test
    ag-ui.state.reduce-test
    ag-ui.sse.sse-test
    ag-ui.serialization.json-test
    ag-ui.conformance.fixtures-test
    ag-ui.protocol.compat-test
    ag-ui.protocol.chunks-test
    ag-ui.protocol.resume-test
    ag-ui.stream-test
    ag-ui.serialization.fields-test
    ag-ui.conformance.cli-test
    ag-ui.server.echo-test
    ag-ui.server.http-test])

(defn run
  [& _]
  (apply require unit-namespaces)
  (let [{:keys [fail error]} (apply t/run-tests unit-namespaces)]
    (System/exit (if (and (zero? fail) (zero? error)) 0 1))))
