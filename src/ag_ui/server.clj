(ns ag-ui.server
  "CLI entry: clojure -M:server"
  (:require [ag-ui.server.http :as http])
  (:gen-class))

(defn -main [& args]
  (let [port (or (some-> (first args) Integer/parseInt)
                 (some-> (System/getenv "PORT") Integer/parseInt)
                 8000)]
    (http/start! {:port port})
    (println (str "AG-UI echo server listening on http://127.0.0.1:" port "/"))
    (println "GET  /  Mochi Protocol Zoo")
    (println "POST /  RunAgentInput with Accept: text/event-stream")
    @(promise)))
