(ns clobaconjure.event
  (:refer-clojure :exclude [next]))

(defprotocol IMapEvent
  (map-event [event f]))

(defrecord Event [event?
                  value
                  initial?
                  next?
                  end?
                  error?
                  has-value?]
  IMapEvent
  (map-event [event f]
    (if (:has-value? event)
      (assoc event :value (f (:value event)) )
      event)))

(defn make-Event [map]
  (map->Event (assoc map :event? true)))

(defn next [value]
  (make-Event {:value      value
               :initial?   false
               :next?      true
               :end?       false
               :error?     false
               :has-value? true}))

(defn initial [value]
  (make-Event {:value      value
               :initial?   true
               :next?      false
               :end?       false
               :error?     false
               :has-value? true}))

(defn end []
  (make-Event {:value      nil
               :initial?   false
               :next?      false
               :end?       true
               :error?     false
               :has-value? false}))

(defn error []
  (make-Event {:value      nil
               :initial?   false
               :next?      false
               :end?       true
               :error?     true
               :has-value? false}))
