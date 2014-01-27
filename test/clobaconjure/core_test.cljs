(ns clobaconjure.core-test
  (:require-macros [cemerick.cljs.test :refer (is deftest with-test run-tests testing test-var)])
  (:require [cemerick.cljs.test :as t]
            [clobaconjure.core :as b]))

(defn expect-events [src & events-expected]
  (let [events-found (atom [])]
    (b/subscribe! src
                  (fn [event]
                    (println "HELLO")
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
    (expect-events (b/sequentially 1000 [{} {:not "empty"}]) {} {:not "empty"})))

(deftest empty-object
  (testing "it should not think an empty object is b/end"
    (expect-events (b/sequentially 1000 [#js {} #js {:not "empty"}]) #js {} #js {:not "empty"})))

#_(deftest filter
  (testing "it should filter values"
    (expect-events
      (-> (b/sequentially 1000 ["a" "b" "c"])
          (b/filter (partial not= "c")))
      "a" "b")))