(ns ag-ui.stream-test
  (:require [clojure.test :refer [deftest is]]
            [ag-ui.stream :as stream]))

(deftest seq-producer
  (is (= '(1 2) (stream/event-seq [1 2]))))

(deftest fn-producer
  (is (= '(:a) (stream/event-seq (fn [] [:a])))))

(deftest delay-producer
  (is (= '(:x) (stream/event-seq (delay [:x])))))

(deftest nil-producer
  (is (= [] (stream/event-seq nil))))

(deftest map-and-filter
  (is (= '(2 4) (stream/filter-events even? [1 2 3 4])))
  (is (= '(2 3) (stream/map-events inc [1 2]))))

(deftest transduce-events
  (is (= [2 4] (stream/transduce-events (comp (filter even?) (map identity)) [1 2 3 4]))))
