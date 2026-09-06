(ns ag-ui.stream
  "Transport-independent event streams.

  A stream is a Clojure seq of events (realized or lazy). Producers may
  also be zero-arg functions that return a seq, or IReduceInit.
  Nothing here knows about SSE or HTTP.")

(defn event-seq
  "Normalize a producer to a seq of events.

  Accepted shapes:
    - a sequential collection
    - a delay / delay-like deref
    - a zero-arg fn returning a seq"
  [producer]
  (cond
    (fn? producer) (event-seq (producer))
    (instance? clojure.lang.IDeref producer) (event-seq @producer)
    (nil? producer) []
    (seqable? producer) (seq producer)
    :else (throw (ex-info "unsupported event producer" {:producer (class producer)}))))

(defn map-events
  [f producer]
  (map f (event-seq producer)))

(defn filter-events
  [pred producer]
  (filter pred (event-seq producer)))

(defn transduce-events
  "Apply a transducer to a producer, returning a vector."
  [xf producer]
  (into [] xf (event-seq producer)))
