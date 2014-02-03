(ns clobaconjure.test.macro
  (:require-macros [cemerick.cljs.test :refer (is deftest with-test run-tests testing test-var done)])
  (:require [cemerick.cljs.test :as t]
            [clobaconjure.core :as b]))

(defn validate-results [-test-ctx events-found events-expected]
  (is -test-ctx (= events-found events-expected) "I must have a message"))


