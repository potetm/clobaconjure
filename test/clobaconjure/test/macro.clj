(ns clobaconjure.test.macro)

(def timeout 1000)

(defmacro defasync [name & body]
  `(cemerick.cljs.test/deftest ~(with-meta name (assoc (meta name) :async true))
     ~name ~@body))

(defmacro with-timeout [timeout & body]
  `(do
     (js/setTimeout
       (fn []
         (cemerick.cljs.test/is (= 0 "Timed out!"))
         (cemerick.cljs.test/done))
       ~timeout)
     ~@body))

;; TODO: figure out how to get rid of the multiple done calls here
(defmacro expect-stream-events [src & events-expected]
  `(do
     (verify-single-subscriber ~'-test-ctx ~src ~@events-expected)
     (verify-switching ~'-test-ctx ~src ~@events-expected)))

(defmacro expect-property-events [src & events-expected]
  `(do
     (verify-single-subscriber ~'-test-ctx ~src ~@events-expected)))
