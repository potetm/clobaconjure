(ns clobaconjure.test.macro
  (:require-macros [cemerick.cljs.test :refer (is deftest with-test run-tests testing test-var done)]
                   [clobaconjure.test.macro :refer (with-timeout)])
  (:require [cemerick.cljs.test :as t]
            [clobaconjure.core :as b]))

(defn validate-results [-test-ctx events-found events-expected]
  (is -test-ctx (= events-found events-expected) "message message message")
  (done))

(defn verify-single-subscriber [-test-ctx src & events-expected]
  (with-timeout 1000
    (let [events-found (atom [])]
      (b/subscribe!
        src
        (fn [event]
          (if (:end? event)
            (validate-results -test-ctx @events-found events-expected)
            (swap! events-found conj (:value event))))))))

(defn verify-switching [-test-ctx src & events-expected]
  (with-timeout 1000
    (let [events-found (atom [])]
      (letfn [(new-sink
                []
                (fn [event]
                  (if (:end? event)
                    (validate-results -test-ctx @events-found events-expected)
                    (do
                      (swap! events-found conj (:value event))
                      (b/subscribe! src (new-sink))
                      b/no-more))))]
        (b/subscribe!
          src
          (new-sink))))))