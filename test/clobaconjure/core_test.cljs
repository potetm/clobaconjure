(ns clobaconjure.core-test
  (:require-macros [cemerick.cljs.test :refer (is deftest with-test run-tests testing test-var)])
  (:require [cemerick.cljs.test :as t]
            [clobaconjure.core :as b]))

(deftest foo-test
  (testing "foo is Hello, World"
    (is (= (b/foo) "Hello, World!"))))
