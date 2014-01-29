(ns clobaconjure.core
  (:refer-clojure :exclude [filter map merge next repeatedly take take-while])
  (:require [cljs.core :as c]))

(def no-more #js ["<no-more>"])
(def more #js ["<more>"])

(def no-more? (partial = no-more))

(defn- set-interval [delay f]
  (js/setInterval f delay))

(defn- clear-interval [id]
  (js/clearInterval id))

(defn- set-timeout [delay f]
  (js/setTimeout f delay))

(defrecord Event [event?
                  value
                  initial?
                  next?
                  end?
                  error?
                  has-value?])

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

(defprotocol ISubscribable
  (subscribe! [eventstream sink]))

(defrecord EventStream [subscribe]
  ISubscribable
  (subscribe! [eventstream sink]
    (subscribe sink)))

(defrecord Property [subscribe]
  ISubscribable
  (subscribe! [property sink]
    (subscribe sink)))

(defn push [subscribers event]
  {:pre [(:event? event)]}
  (let [remove #(vec (concat (subvec %1 0 %2)
                             (subvec %1 (inc %2) (count %1))))]
    (doseq [[s i] (c/map vector @subscribers (iterate inc 0))
            :let [reply (s event)]]
      (when (or (no-more? reply) (:end? reply))
        (swap! subscribers remove i))
      (if (empty? @subscribers)
        no-more
        more))))

(defn- make-subscribe [subscribe-prev handler subscribers]
  (fn [sink]
    (swap! subscribers conj sink)
    (when (= (count @subscribers) 1)
      (subscribe-prev handler))))

(defn eventstream [subscribe]
  (let [subscribers (atom [])
        handler (partial push subscribers)]
    (->EventStream (make-subscribe subscribe handler subscribers))))

(defn property [subscribe init-value]
  (let [current-value (atom init-value)
        subscribers (atom [])
        handler (fn [event]
                  (when-not (:end? event)
                    (reset! current-value event))
                  (push subscribers event))
        subscribe (make-subscribe subscribe handler subscribers)
        my-subscribe (fn [sink]
                       (when @current-value
                         (sink (initial @current-value)))
                       (subscribe sink))]
    (->Property my-subscribe)))

(defn to-property [es init-value]
  (property (:subscribe es) init-value))

(defn- from-eventstream [es handler]
  (let [subscribers (atom [])]
    (->EventStream
      (make-subscribe
        (:subscribe es)
        (partial handler subscribers)
        subscribers))))

(defn filter [es f]
  (let [handler (fn [subscribers event]
                  (if (or (:end? event) (f (:value event)))
                    (push subscribers event)
                    more))]
    (from-eventstream es handler)))

(defn map [es f]
  (let [handler (fn [subscribers event]
                  (push subscribers
                        (if (:end? event)
                          event
                          (next (f (:value event))))))]
    (from-eventstream es handler)))

(defn take-while [es f]
  (let [handler (fn [subscribers event]
                  (if (or (:end? event) (f (:value event)))
                    (push subscribers event)
                    (do
                      (push subscribers (end))
                      no-more)))]
    (from-eventstream es handler)))

(defn merge [left right]
  (eventstream
    (fn [sink]
      (let [end-me? (atom false)
            smart-sink (fn [event]
                         (if (:end? event)
                           (if @end-me?
                             (sink (end))
                             (do
                               (reset! end-me? true)
                               more))
                           (sink event)))]
        (subscribe! left smart-sink)
        (subscribe! right smart-sink)))))

(defn from-poll [delay poll]
  (eventstream
    (fn [sink]
      (let [id (atom nil)
            unbind #(clear-interval @id)
            emitter (fn []
                      (let [value (poll)
                            reply (sink value)]
                        (when (or (no-more? reply) (:end? value))
                          (unbind))))]
        (reset! id (set-interval delay emitter))
        unbind))))

(defn later [delay value]
  (eventstream
    (fn [sink]
      (set-timeout
        delay
        (fn []
          (sink (next value))
          (sink (end)))))))

(defn sequentially [delay values]
  (let [values (atom values)
        poll (fn []
               (let [value (first @values)]
                 (if value
                   (do (swap! values rest)
                       (next value))
                   (end))))]
    (from-poll delay poll)))

#_(defn sequentially [delay values]
  (letfn [(schedule
            [sink values]
            (set-timeout
              delay
              (fn []
                (if (empty? values)
                  (sink (end))
                  (when-not (no-more? (sink (next (first values))))
                    (schedule sink (rest values)))))))]
    (eventstream #(schedule % values))))

(defn from-array [values]
  (eventstream
    (fn [sink]
      (doseq [v values]
        (sink (next v)))
      (sink (end)))))
