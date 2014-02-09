(ns clobaconjure.test.macro)

(def timeout 1000)

(defmacro defasync [name & body]
  `(cemerick.cljs.test/deftest ~(with-meta name (assoc (meta name) :async true))
     ~name ~@body))

(defmacro with-timeout [timeout done? & body]
  `(do
     (js/setTimeout
       (fn []
         (when (not (deref ~done?))
           (cemerick.cljs.test/is false "TIMED OUT!")
           (cemerick.cljs.test/done)))
       ~timeout)
     ~@body))

(defmacro later [time & body]
  `(do
     (js/setTimeout
       (fn []
         ~@body)
       ~time)))

;; TODO: figure out how to get rid of the multiple done calls here
(defmacro expect-stream-events [src & events-expected]
  `(do
     (verify-single-subscriber ~'-test-ctx ~src ~@events-expected)
#_     (verify-switching ~'-test-ctx ~src ~@events-expected)))

(defmacro expect-property-events [src & events-expected]
  `(do
     (verify-single-subscriber ~'-test-ctx ~src ~@events-expected)))
