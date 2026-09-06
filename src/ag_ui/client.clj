(ns ag-ui.client
  "CLI: clojure -M:client http://127.0.0.1:8000/"
  (:require [ag-ui.client.http :as http]
            [ag-ui.serialization.json :as json])
  (:gen-class))

(defn -main [& args]
  (let [url (or (first args) "http://127.0.0.1:8000/")
        text (or (second args) "Hello")
        input {:thread-id "cli"
               :run-id "cli-run"
               :messages [{:id "m1" :role "user" :content text}]}
        result (http/run-agent {:url url :input input})]
    (println (json/encode-json result))
    (when-not (:ok result)
      (System/exit 1))))
