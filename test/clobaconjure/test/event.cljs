(ns clobaconjure.test.event
  (:require-macros [cemerick.cljs.test :refer (is deftest with-test run-tests testing test-var done)])
  (:require [clobaconjure.event :as e]))

(deftest map-event
  (testing "events should be mappable"
    (is (= (e/initial "b") (e/map-event (e/initial "a") (constantly "b"))))))

(deftest map-event-no-value
  (testing "even events that have no value"
    (is (= (e/end) (e/map-event (e/end) (constantly "b"))))))
