(ns clobaconjure.test.macro
  (:require-macros [cemerick.cljs.test :refer (is deftest with-test run-tests testing test-var done)]
                   [clobaconjure.test.macro :refer (with-timeout later)])
  (:require [cemerick.cljs.test :as t]
            [clobaconjure.core :as b]))

(defn verify-cleanup [-test-ctx src]
  (is -test-ctx (= (count (b/subscribers src)) 0) "Cleaning up")
  (done))

(defn verify-results [-test-ctx done? src events-found events-expected]
  (is -test-ctx (= events-found events-expected) "Checking results")
  (reset! done? true))

(defn verify-single-subscriber [-test-ctx src & events-expected]
  (let [done? (atom false)]
    (with-timeout 1000 done?
      (let [events-found (atom [])]
        (b/subscribe!
          src
          (fn [event]
            (if (:end? event)
              (verify-results -test-ctx done? src @events-found events-expected)
              (swap! events-found conj (:value event)))))
        (later 200
          (verify-cleanup -test-ctx src))))))

(defn verify-switching [-test-ctx src & events-expected]
  (let [done? (atom false)]
    (with-timeout 1000 done?
      (let [events-found (atom [])]
        (letfn [(new-sink
                  []
                  (fn [event]
                    (if (:end? event)
                      (verify-results -test-ctx done? src @events-found events-expected)
                      (do (swap! events-found conj (:value event))
                          (b/subscribe! src (new-sink))
                          b/no-more))))]
          (b/subscribe!
            src
            (new-sink))
          (later 200
            (verify-cleanup -test-ctx src)))))))