(ns clobaconjure.core-test
  (:require-macros [clobaconjure.macro :refer (defasync expect-events) :as m]
                   [cemerick.cljs.test :refer (is deftest with-test run-tests testing test-var done)])
  (:require [cemerick.cljs.test :as t]
            [clobaconjure.core :as b]))

(defasync later
  (testing "it should send a single event and end"
    (expect-events
      (b/later 10 "hipsta!")
      "hipsta!")))

(defasync sequentially
  (testing "it should send events and end"
    (expect-events
      (b/sequentially 10 ["hipsta 1" "hipsta 2"])
      "hipsta 1" "hipsta 2")))

(defasync empty-map
  (testing "it should not think an empty map is end"
    (expect-events
      (b/from-array [{} {:not "empty"}])
      {} {:not "empty"})))

(defasync empty-object
  (testing "it should not think an empty object is end"
    (let [empty #js {}
          not-empty #js {:not "empty"}]
      (expect-events (b/from-array [empty not-empty]) empty not-empty))))

(defasync filtering
  (testing "it should filter values"
    (expect-events
      (-> (b/from-array ["a" "b" "c"])
          (b/filter (partial not= "c")))
      "a" "b")))

(defasync mapping
  (testing "it should map values"
    (expect-events
      (-> (b/from-array [1 2 3])
          (b/map inc))
      2 3 4)))

(defasync filter-and-map
  (testing "it should be composable"
    (expect-events
      (-> (b/from-array [1 2 3 4])
          (b/filter even?)
          (b/map inc))
      3 5)))

(defasync taking-while
  (testing "it should take while"
    (expect-events
      (-> (b/from-array [1 2 3 4])
          (b/take-while (partial > 3)))
      1 2)))

(defasync merging
  (testing "it should be mergable"
    (expect-events
      (-> (b/from-array [1 2 3 4])
          (b/merge (b/from-array [5 6 7 8])))
      1 2 3 4 5 6 7 8)))

(defasync property
  (testing "delivers current value and changes"
    (expect-events
      (-> (b/later 50 "b")
          (b/to-property "a"))
      "a" "b")))

(defasync taking-n
  (testing "it takes the first n values"
    (expect-events

      (-> (b/from-array [1 2 3 4 5])
          (b/take 3))
      1 2 3)))

(defasync repeating
  (testing "it repeats"
    (expect-events
      (-> (b/repeatedly 10 [1 2 3])
          (b/take 6))
      1 2 3 1 2 3)))