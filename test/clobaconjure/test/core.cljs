(ns clobaconjure.test.core
  (:require-macros [clobaconjure.test.macro :refer (defasync expect-stream-events expect-property-events later) :as m]
                   [cemerick.cljs.test :refer (is deftest with-test run-tests testing test-var done)])
  (:require [clobaconjure.core :as b]
            [clobaconjure.test.macro :as m]))

;; TODO: All of these tests are pretty weak because they depend heavily on timing.
;; It would be much better to us a function/macro that polls to see if the stream
;; is done rather than have to tweak wait times.

(defasync on-value!
  (testing "it should receive values"
    (let [values (atom [])]
      (-> (b/sequentially 1 ["looza!" "foo!"])
          (b/on-value! #(swap! values conj %)))
      (later 25
             (is (= @values ["looza!" "foo!"]))
             (done)))))

(defasync unsubscribe
  (testing "it should unsubscribe"
    (let [values (atom [])
          unsub (atom nil)]
      (reset! unsub
              (-> (b/sequentially 1 [1 2])
                  (b/on-value!
                    (fn [v]
                      (swap! values conj v)
                      (@unsub)))))
      (later 3
             (is (= @values [1]))
             (done)))))

(defasync later
  (testing "it should send a single event and end"
    (expect-stream-events
      (b/later 1 "hipsta!")
      "hipsta!")))

(defasync sequentially
  (testing "it should send events and end"
    (expect-stream-events
      (b/sequentially 1 ["hipsta 1" "hipsta 2"])
      "hipsta 1" "hipsta 2")))

(defasync empty-map
  (testing "it should not think an empty map is end"
    (expect-stream-events
      (b/from-array [{} {:not "empty"}])
      {} {:not "empty"})))

(defasync empty-object
  (testing "it should not think an empty object is end"
    (let [empty #js {}
          not-empty #js {:not "empty"}]
      (expect-stream-events
        (b/from-array [empty not-empty])
        empty not-empty))))

(defasync filtering
  (testing "it should filter values"
    (expect-stream-events
      (-> (b/from-array ["a" "b" "c"])
          (b/filter (partial not= "c")))
      "a" "b")))

(defasync mapping
  (testing "it should map values"
    (expect-stream-events
      (-> (b/from-array [1 2 3])
          (b/map inc))
      2 3 4)))

(defasync filter-and-map
  (testing "it should be composable"
    (expect-stream-events
      (-> (b/from-array [1 2 3 4])
          (b/filter even?)
          (b/map inc))
      3 5)))

(defasync taking-while
  (testing "it should take while"
    (expect-stream-events
      (-> (b/from-array [1 2 3 4])
          (b/take-while (partial > 3)))
      1 2)))

(defasync merging
  (testing "it should be mergable"
    (expect-stream-events
      (-> (b/from-array [1 2 3 4])
          (b/merge (b/from-array [5 6 7 8])))
      1 2 3 4 5 6 7 8)))

(defasync property
  (testing "delivers current value and changes"
    (expect-property-events
      (-> (b/later 5 "b")
          (b/to-property "a"))
      "a" "b")))

(defasync taking-n
  (testing "it takes the first n values"
    (expect-stream-events
      (-> (b/from-array [1 2 3 4 5])
          (b/take 3))
      1 2 3)))

(defasync repeating
  (testing "it repeats"
    (expect-stream-events
      (-> (b/repeatedly 1 [1 2 3])
          (b/take 5))
      1 2 3 1 2)))

(defasync constant
  (testing "that it's constant"
    (expect-property-events
      (b/constant "wat")
      "wat")))
