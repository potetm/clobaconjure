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

(defmacro ^:private validate-results [events-found events-expected]
  `(do
     (cemerick.cljs.test/is ~'-test-ctx
                            (= ~events-found
                               ~events-expected)
                            "I must have a message")
     (cemerick.cljs.test/done)))

(defmacro verify-single-subscriber [src & events-expected]
  `(with-timeout ~timeout
     (let [events-found# (atom [])]
       (clobaconjure.core/subscribe!
         ~src
         (fn [event#]
           (if (:end? event#)
             (validate-results @events-found# (vector ~@events-expected))
             (swap! events-found# conj (:value event#))))))))

(defmacro verify-switching [src & events-expected]
  `(with-timeout ~timeout
     (let [src# ~src
           events-found# (atom [])]
       (letfn [(new-sink#
                 []
                 (fn [event#]
                   (if (:end? event#)
                     (validate-results @events-found# (vector ~@events-expected))
                     (do
                       (swap! events-found# conj (:value event#))
                       (clobaconjure.core/subscribe! src# (new-sink#))
                       clobaconjure.core/no-more))))]
         (clobaconjure.core/subscribe!
           src#
           (new-sink#))))))

(defmacro expect-stream-events [src & events-expected]
  `(do
     (verify-single-subscriber ~src ~@events-expected)
     (verify-switching ~src ~@events-expected)))

(defmacro expect-property-events [src & events-expected]
  `(do
     (verify-single-subscriber ~src ~@events-expected)))

(defmacro defstreamtest [title desc & body]
  `(do
     (defasync ~(symbol (str (name title) "-single-subscriber"))
        (cemerick.cljs.test/testing ~desc
          (verify-single-subscriber ~(first body) ~@(rest body))))
     (defasync ~(symbol (str (name title) "-switching"))
        (cemerick.cljs.test/testing ~desc
          (verify-switching ~(first body) ~@(rest body))))))

