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
        resumes (core/run-resume-fixtures)
        endpoint (or (System/getenv "AG_UI_EXTERNAL_URL")
                     (let [hit (first (filter #(or (= "--endpoint" %) (str/starts-with? % "http")) args))]
                       (cond
                         (= "--endpoint" (first args)) (second args)
                         (and hit (not= "--endpoint" hit)) hit
                         :else nil)))
        live (when endpoint
               (println)
               (println "Live endpoint" endpoint)
               (core/check-endpoint endpoint))
        valid-fail (count (remove :ok valid))
        mal-fail (count (remove :ok malformed))
        resume-fail (count (remove :ok resumes))
        live-fail (if (and live (not (:ok live))) 1 0)
        n (+ valid-fail mal-fail resume-fail live-fail)]
    (println "Valid streams")
    (print-valid valid)
    (println)
    (println "Malformed fixtures (must reject)")
    (print-malformed malformed)
    (println)
    (println "Resume coverage")
    (doseq [r resumes]
      (if (:skipped r)
        (println "· skip" (:name r))
        (if (:ok r)
          (println (pass (:name r)))
          (println (fail (:name r))))))
    (when live
      (println)
      (if (:ok live)
        (println (pass (str "live " endpoint " (" (count (:events live)) " events)")))
        (println (fail (str "live " endpoint " " (pr-str (select-keys live [:kind :status :error])))))))
    (println)
    (if (zero? n)
      (println "All checks passed")
      (println n "failure(s)"))
    (System/exit (if (zero? n) 0 1))))
