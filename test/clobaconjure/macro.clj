(ns clobaconjure.macro)

(defmacro defasync [name & body]
  `(cemerick.cljs.test/deftest ~(with-meta name (assoc (meta name) :async true))
     ~name ~@body))

(defmacro expect-events [src & events-expected]
  `(js/setTimeout #(cemerick.cljs.test/done) 1000)
  `(let [events-found# (atom [])]
     (clobaconjure.core/subscribe!
       ~src
       (fn [event#]
         (if (:end? event#)
           (do
             (cemerick.cljs.test/is ~'-test-ctx
                                    (= @events-found#
                                       (vector ~@events-expected))
                                    "I must have a message")
             (cemerick.cljs.test/done))
           (swap! events-found# conj (:value event#)))))))
