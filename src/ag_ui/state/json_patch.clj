(ns ag-ui.state.json-patch
  "RFC 6902 JSON Patch, applied immutably.

  Structural malformation is the caller's problem (protocol validation).
  Application failure returns {:ok false :reason ...} and leaves the document."
  (:require [clojure.string :as str]))

(defn- decode-token [token]
  (-> token
      (str/replace #"~1" "/")
      (str/replace #"~0" "~")))

(defn parse-pointer
  [pointer]
  (cond
    (nil? pointer) ::invalid
    (not (string? pointer)) ::invalid
    (= pointer "") []
    (not (str/starts-with? pointer "/")) ::invalid
    :else (mapv decode-token (rest (str/split pointer #"/")))))

(defn- array-index [token arr]
  (cond
    (= token "-") (count arr)
    (re-matches #"0|[1-9][0-9]*" token) (Long/parseLong token)
    :else ::invalid))

(defn- get-in-doc [doc tokens]
  (reduce (fn [acc token]
            (cond
              (= acc ::missing) ::missing
              (map? acc) (if (contains? acc token)
                           (get acc token)
                           ::missing)
              (vector? acc)
              (let [i (array-index token acc)]
                (if (or (= i ::invalid) (neg? i) (>= i (count acc)))
                  ::missing
                  (nth acc i)))
              :else ::missing))
          doc
          tokens))

(defn- assoc-in-doc [doc tokens value]
  (if (empty? tokens)
    value
    (let [token (first tokens)
          rest-tokens (rest tokens)]
      (cond
        (map? doc)
        (let [child (get doc token)
              child (if (and (seq rest-tokens) (nil? child)) {} child)]
          (assoc doc token (assoc-in-doc child rest-tokens value)))

        (vector? doc)
        (let [i (array-index token doc)]
          (when (and (not= i ::invalid) (<= 0 i (count doc)))
            (if (empty? rest-tokens)
              (if (= i (count doc))
                (conj doc value)
                (assoc doc i value))
              (when (< i (count doc))
                (assoc doc i (assoc-in-doc (nth doc i) rest-tokens value))))))

        :else nil))))

(defn- remove-in-doc [doc tokens]
  (if (= 1 (count tokens))
    (let [token (first tokens)]
      (cond
        (map? doc) (if (contains? doc token) (dissoc doc token) ::missing)
        (vector? doc)
        (let [i (array-index token doc)]
          (if (or (= i ::invalid) (neg? i) (>= i (count doc)))
            ::missing
            (into (subvec doc 0 i) (subvec doc (inc i)))))
        :else ::missing))
    (let [token (first tokens)
          more (rest tokens)]
      (cond
        (map? doc)
        (if (contains? doc token)
          (let [child (remove-in-doc (get doc token) more)]
            (if (= child ::missing) ::missing (assoc doc token child)))
          ::missing)
        (vector? doc)
        (let [i (array-index token doc)]
          (if (or (= i ::invalid) (neg? i) (>= i (count doc)))
            ::missing
            (let [child (remove-in-doc (nth doc i) more)]
              (if (= child ::missing) ::missing (assoc doc i child)))))
        :else ::missing))))

(defn- apply-op [doc op]
  (let [opcode (:op op)
        path (parse-pointer (:path op))]
    (if (= path ::invalid)
      {:ok false :reason (str "invalid JSON pointer: " (pr-str (:path op)))}
      (case opcode
        "add"
        (if (empty? path)
          {:ok true :doc (:value op)}
          (if-let [next (assoc-in-doc doc path (:value op))]
            {:ok true :doc next}
            {:ok false :reason (str "add failed at " (:path op))}))

        "replace"
        (if (empty? path)
          {:ok true :doc (:value op)}
          (if (= ::missing (get-in-doc doc path))
            {:ok false :reason (str "replace target missing: " (:path op))}
            (if-let [next (assoc-in-doc doc path (:value op))]
              {:ok true :doc next}
              {:ok false :reason (str "replace failed at " (:path op))})))

        "remove"
        (if (empty? path)
          {:ok false :reason "cannot remove the root"}
          (let [next (remove-in-doc doc path)]
            (if (= next ::missing)
              {:ok false :reason (str "remove target missing: " (:path op))}
              {:ok true :doc next})))

        "move"
        (let [from (parse-pointer (:from op))]
          (if (= from ::invalid)
            {:ok false :reason (str "invalid from pointer: " (pr-str (:from op)))}
            (let [v (get-in-doc doc from)]
              (if (= v ::missing)
                {:ok false :reason (str "move source missing: " (:from op))}
                (let [removed (if (empty? from) ::missing (remove-in-doc doc from))]
                  (if (= removed ::missing)
                    {:ok false :reason (str "move remove failed: " (:from op))}
                    (if (empty? path)
                      {:ok true :doc v}
                      (if-let [next (assoc-in-doc removed path v)]
                        {:ok true :doc next}
                        {:ok false :reason (str "move add failed at " (:path op))}))))))))

        "copy"
        (let [from (parse-pointer (:from op))]
          (if (= from ::invalid)
            {:ok false :reason (str "invalid from pointer: " (pr-str (:from op)))}
            (let [v (get-in-doc doc from)]
              (if (= v ::missing)
                {:ok false :reason (str "copy source missing: " (:from op))}
                (if (empty? path)
                  {:ok true :doc v}
                  (if-let [next (assoc-in-doc doc path v)]
                    {:ok true :doc next}
                    {:ok false :reason (str "copy add failed at " (:path op))}))))))

        "test"
        (let [v (get-in-doc doc path)]
          (if (= v (:value op))
            {:ok true :doc doc}
            {:ok false :reason (str "test failed at " (:path op))}))

        {:ok false :reason (str "unrecognised op " opcode)}))))

(defn apply-patch
  "Apply a sequence of operations atomically.
  Returns {:ok true :doc ...} or {:ok false :reason ... :doc original}."
  [document operations]
  (if-not (vector? operations)
    {:ok false :reason "patch is not an array" :doc document}
    (loop [doc document
           ops operations]
      (if (empty? ops)
        {:ok true :doc doc}
        (let [op (first ops)]
          (if-not (map? op)
            {:ok false :reason "operation is not an object" :doc document}
            (let [{:keys [ok doc reason]} (apply-op doc op)]
              (if ok
                (recur doc (rest ops))
                {:ok false :reason reason :doc document}))))))))
