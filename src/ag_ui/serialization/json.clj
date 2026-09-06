(ns ag-ui.serialization.json
  "Canonical JSON encoding/decoding for AG-UI wire objects.

  Protocol envelope fields become kebab-case keywords. Opaque JSON
  (state, snapshots, metadata values, CUSTOM/RAW payloads, patch
  `value`s) keeps string keys so JSON Patch pointers match the wire."
  (:require [clojure.data.json :as json]
            [ag-ui.serialization.fields :as fields]))

(def opaque-keys
  #{:snapshot :raw-event :value :event :payload :state
    :forwarded-props :result :metadata :parameters :response-schema})

(defn stringify-keys
  [x]
  (cond
    (map? x) (into {} (map (fn [[k v]]
                             [(cond
                                (keyword? k) (name k)
                                (string? k) k
                                :else (str k))
                              (stringify-keys v)])
                           x))
    (vector? x) (mapv stringify-keys x)
    :else x))

(defn- clj->wire-opaque [x]
  (stringify-keys x))

(defn clj->wire-value
  [x]
  (cond
    (map? x)
    (into {}
          (map (fn [[k v]]
                 (let [wk (fields/key->wire k)
                       ck (if (keyword? k) k (fields/key->clj k))]
                   [wk (if (opaque-keys ck)
                         (clj->wire-opaque v)
                         (clj->wire-value v))]))
               x))
    (vector? x) (mapv clj->wire-value x)
    (seq? x) (mapv clj->wire-value x)
    (keyword? x) (name x)
    :else x))

(defn- patch-op->clj [m]
  (cond-> {:op (get m "op")}
    (contains? m "path") (assoc :path (get m "path"))
    (contains? m "from") (assoc :from (get m "from"))
    (contains? m "value") (assoc :value (stringify-keys (get m "value")))))

(defn wire->clj-value
  [x]
  (cond
    (map? x)
    (into {}
          (map (fn [[k v]]
                 (let [ck (fields/key->clj k)]
                   [ck (cond
                         (opaque-keys ck) (stringify-keys v)
                         (and (= ck :delta) (vector? v) (some map? v)
                              (some #(contains? % "op") v))
                         (mapv (fn [item]
                                 (if (map? item) (patch-op->clj item) item))
                               v)
                         (and (= ck :patch) (vector? v))
                         (mapv (fn [item]
                                 (if (map? item) (patch-op->clj item) item))
                               v)
                         :else (wire->clj-value v))]))
               x))
    (vector? x) (mapv wire->clj-value x)
    :else x))

(defn encode-json
  [obj]
  (json/write-str (clj->wire-value obj)))

(defn decode-json
  [s]
  (when (or (nil? s) (and (string? s) (not (re-find #"\S" s))))
    (throw (ex-info "empty JSON" {:reason :empty})))
  (wire->clj-value (json/read-str s)))

(defn decode-json-strict
  [s]
  (let [v (decode-json s)]
    (when-not (map? v)
      (throw (ex-info "AG-UI events and run inputs must be JSON objects"
                      {:reason :not-object :value v})))
    v))
