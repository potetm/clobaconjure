(ns clobaconjure.core-test
  (:require-macros [cemerick.cljs.test :refer (is deftest with-test run-tests testing test-var)])
  (:require [cemerick.cljs.test :as t]
            [clobaconjure.core :as b]))

(defn expect-events [src & events-expected]
  (let [events-found (atom [])]
    (b/subscribe! src
                  (fn [event]
                    (if (= event b/end)
                      (is (= @events-found events-expected))
                      (swap! events-found conj event))))))

(deftest later
  (testing "it should send a single event and end"
    (expect-events (b/later 10 "hipsta!") "hipsta!")))

(deftest sequentially
  (testing "it should send events and end"
    (expect-events (b/sequentially 1000 ["hipsta 1" "hipsta 2"]) "hipsta 1" "hipsta 2")))

(deftest empty-map
  (testing "it should not think an empty map is b/end"
    (expect-events (b/from-array [{} {:not "empty"}]) {} {:not "empty"})))

(deftest empty-object
  (testing "it should not think an empty object is b/end"
    (let [empty #js {}
          not-empty #js {:not "empty"}]
      (expect-events (b/from-array [empty not-empty]) empty not-empty))))

(deftest filtering
  (testing "it should filter values"
    (expect-events
      (-> (b/from-array ["a" "b" "c"])
          (b/filter (partial not= "c")))
      "a" "b")))

(deftest mapping
  (testing "it should map values"
    (expect-events
      (-> (b/from-array [1 2 3])
          (b/map inc))
      2 3 4)))

(deftest filter-and-map
  (testing "it should be composable"
    (expect-events
      (-> (b/from-array [1 2 3 4])
          (b/filter even?)
          (b/map inc))
      3 5)))