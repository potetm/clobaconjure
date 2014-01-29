(ns clobaconjure.core-test
  (:require-macros [cemerick.cljs.test :refer (is deftest with-test run-tests testing test-var done)])
  (:require [cemerick.cljs.test :as t]
            [clobaconjure.core :as b]))

(defn expect-events [-test-ctx src & events-expected]
  (js/setTimeout #(done) 1000)
  (let [events-found (atom [])]
    (b/subscribe! src
                  (fn [event]
                    (if (= event b/end)
                      (do
                        (is -test-ctx (= @events-found events-expected) "I need a message")
                        (done))
                      (swap! events-found conj event))))))

(deftest ^:async later
  (testing "it should send a single event and end"
    (expect-events -test-ctx (b/later 10 "hipsta!") "hipsta!")))

(deftest ^:async sequentially
  (testing "it should send events and end"
    (expect-events -test-ctx (b/sequentially 10 ["hipsta 1" "hipsta 2"]) "hipsta 1" "hipsta 2")))

(deftest ^:async empty-map
  (testing "it should not think an empty map is b/end"
    (expect-events -test-ctx (b/from-array [{} {:not "empty"}]) {} {:not "empty"})))

(deftest ^:async empty-object
  (testing "it should not think an empty object is b/end"
    (let [empty #js {}
          not-empty #js {:not "empty"}]
      (expect-events -test-ctx (b/from-array [empty not-empty]) empty not-empty))))

(deftest ^:async filtering
  (testing "it should filter values"
    (expect-events
      -test-ctx
      (-> (b/from-array ["a" "b" "c"])
          (b/filter (partial not= "c")))
      "a" "b")))

(deftest ^:async mapping
  (testing "it should map values"
    (expect-events
      -test-ctx
      (-> (b/from-array [1 2 3])
          (b/map inc))
      2 3 4)))

(deftest ^:async filter-and-map
  (testing "it should be composable"
    (expect-events
      -test-ctx
      (-> (b/from-array [1 2 3 4])
          (b/filter even?)
          (b/map inc))
      3 5)))

(deftest ^:async taking-while
  (testing "it should take while"
    (expect-events
      -test-ctx
      (-> (b/from-array [1 2 3 4])
          (b/take-while (partial > 3)))
      1 2)))

(deftest ^:async merging
  (testing "it should be mergable"
    (expect-events
      -test-ctx
      (-> (b/from-array [1 2 3 4])
          (b/merge (b/from-array [5 6 7 8])))
      1 2 3 4 5 6 7 8)))

(deftest ^:async property
  (testing "delivers current value and changes"
    (expect-events
      -test-ctx
      (-> (b/later 50 "b")
          (b/to-property "a"))
      "a" "b")))