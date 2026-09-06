(ns ag-ui.conformance.cli
  (:require [clojure.string :as str]
            [ag-ui.conformance.core :as core])
  (:gen-class))

(defn- pass [s] (str "✓ " s))
(defn- fail [s] (str "✗ " s))

(defn- print-valid [results]
  (doseq [r results]
    (if (:ok r)
      (println (pass (:name r)))
      (do
        (println (fail (:name r)))
        (println "  stage:" (pr-str (:stage r)))
        (when (:violation r)
          (println "  Expected a valid lifecycle")
          (println "  Received:" (get-in r [:violation :event-type]))
          (println "  " (get-in r [:violation :message])))
        (when (:failures r)
          (println "  " (pr-str (take 3 (:failures r)))))
        (println "  See:" (:file r))))))

(defn- print-malformed [results]
  (doseq [r results]
    (if (:ok r)
      (println (pass (str "reject " (:name r))))
      (do
        (println (fail (str "should reject " (:name r))))
        (println "  See:" (:file r))))))

(defn -main [& args]
  (println "AG-UI Conformance")
  (println)
  (let [valid (core/run-valid-fixtures)
        malformed (core/run-malformed-fixtures)
        endpoint (first (filter #(or (= "--endpoint" %) (str/starts-with? % "http")) args))
        url (cond
              (= "--endpoint" (first args)) (second args)
              (and endpoint (not= "--endpoint" endpoint)) endpoint
              :else nil)
        live (when url
               (println)
               (println "Live endpoint" url)
               (core/check-endpoint url))
        valid-fail (count (remove :ok valid))
        mal-fail (count (remove :ok malformed))
        live-fail (if (and live (not (:ok live))) 1 0)
        n (+ valid-fail mal-fail live-fail)]
    (println "Valid streams")
    (print-valid valid)
    (println)
    (println "Malformed fixtures (must reject)")
    (print-malformed malformed)
    (when live
      (println)
      (if (:ok live)
        (println (pass (str "live " url " (" (count (:events live)) " events)")))
        (println (fail (str "live " url " " (pr-str (select-keys live [:kind :status :error])))))))
    (println)
    (if (zero? n)
      (println "All checks passed")
      (println n "failure(s)"))
    (System/exit (if (zero? n) 0 1))))
