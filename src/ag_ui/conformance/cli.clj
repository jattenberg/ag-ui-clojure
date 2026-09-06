(ns ag-ui.conformance.cli
  (:require [clojure.string :as str]
            [ag-ui.conformance.core :as core])
  (:gen-class))

(defn- pass [s] (str "✓ " s))
(defn- fail [s] (str "✗ " s))

(defn parse-args
  [args]
  (loop [xs (vec args)
         acc {:profile :all :endpoint (System/getenv "AG_UI_EXTERNAL_URL")}]
    (cond
      (empty? xs) acc
      (= "--profile" (first xs))
      (recur (vec (drop 2 xs)) (assoc acc :profile (keyword (second xs))))
      (= "--endpoint" (first xs))
      (recur (vec (drop 2 xs)) (assoc acc :endpoint (second xs)))
      (and (string? (first xs)) (str/starts-with? (first xs) "http"))
      (recur (vec (rest xs)) (assoc acc :endpoint (first xs)))
      :else
      (recur (vec (rest xs)) acc))))

(defn- print-results [title results]
  (println title)
  (doseq [r results]
    (cond
      (:skipped r)
      (println "· skip" (:name r))
      (:ok r)
      (println (pass (str (:name r)
                          (when (= "reject" (:expected r)) " (reject)"))))
      :else
      (do
        (println (fail (:name r)))
        (println "  expected:" (:expected r) "profile:" (:profile r) "stage:" (pr-str (:stage r)))
        (when (:violation r)
          (println " " (get-in r [:violation :message])))
        (when-let [v (get-in r [:check :violation])]
          (println " " (:message v)))
        (when (:failures r)
          (println " " (pr-str (take 2 (:failures r)))))
        (println "  See:" (:file r)))))
  (println))

(defn -main [& args]
  (println "AG-UI Conformance")
  (println)
  (let [{:keys [profile endpoint]} (parse-args args)
        profiles (if (= profile :all) [:producer :consumer] [profile])
        sections (mapv (fn [p]
                         {:profile p :results (core/run-profile p)})
                       profiles)
        resumes (core/run-resume-fixtures)
        live (when endpoint
               (println "Live endpoint" endpoint)
               (core/check-endpoint endpoint))
        profile-fail (count (mapcat (fn [s] (remove :ok (:results s))) sections))
        resume-fail (count (remove :ok resumes))
        live-fail (if (and live (not (:ok live))) 1 0)
        n (+ profile-fail resume-fail live-fail)]
    (doseq [{:keys [profile results]} sections]
      (print-results (str "Profile " (name profile)) results))
    (println "Resume coverage")
    (doseq [r resumes]
      (if (:skipped r)
        (println "· skip" (:name r))
        (if (:ok r)
          (println (pass (:name r)))
          (println (fail (:name r))))))
    (println)
    (when live
      (if (:ok live)
        (println (pass (str "live " endpoint " (" (count (:events live)) " events)")))
        (println (fail (str "live " endpoint " " (pr-str (select-keys live [:kind :status :error]))))))
      (println))
    (if (zero? n)
      (println "All checks passed")
      (println n "failure(s)"))
    (System/exit (if (zero? n) 0 1))))
