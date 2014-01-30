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

(defn push [sinks event]
  {:pre [(:event? event)]}
  (let [remove #(vec (concat (subvec %1 0 %2)
                             (subvec %1 (inc %2) (count %1))))]
    (doseq [[s i] (c/map vector @sinks (iterate inc 0))
            :let [reply (s event)]]
      (when (or (no-more? reply) (:end? event))
        (swap! sinks remove i))
      (if (empty? @sinks)
        no-more
        more))))

(defn- make-unsubscribe [])

(defn- make-subscribe [subscribe-prev handler sinks]
  (fn [sink]
    (swap! sinks conj sink)
    (when (= (count @sinks) 1)
      (subscribe-prev handler))))

(defn eventstream [subscribe]
  (let [sinks (atom [])
        handler (partial push sinks)]
    (->EventStream (make-subscribe subscribe handler sinks))))

(defn property [subscribe init-value]
  (let [current-value (atom init-value)
        sinks (atom [])
        handler (fn [event]
                  (when-not (:end? event)
                    (reset! current-value event))
                  (push sinks event))
        subscribe (make-subscribe subscribe handler sinks)
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
  (let [handler (fn [sinks event]
                  (if (or (:end? event) (f (:value event)))
                    (push sinks event)
                    more))]
    (from-eventstream es handler)))

(defn map [es f]
  (let [handler (fn [sinks event]
                  (push sinks
                        (if (:end? event)
                          event
                          (next (f (:value event))))))]
    (from-eventstream es handler)))

(defn take-while [es f]
  (let [handler (fn [sinks event]
                  (if (or (:end? event) (f (:value event)))
                    (push sinks event)
                    (do
                      (push sinks (end))
                      no-more)))]
    (from-eventstream es handler)))

(defn take [es num]
  (let [n (atom num)
        handler (fn [sinks event]
                  (if (or (:end? event) (>= (swap! n dec) 0))
                    (push sinks event)
                    (do (push sinks (end))
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

(defn sequentially [delay values]
  (let [values (atom values)
        poll (fn []
               (let [value (first @values)]
                 (if value
                   (do (swap! values rest)
                       (next value))
                   (end))))]
    (from-poll delay poll)))

(defn later [delay value]
  (sequentially delay [value]))

(defn repeatedly [delay values]
  (let [index (atom -1)
        length (count values)
        poll (fn []
               (->> (mod (swap! index inc) length)
                    (get values)
                    next))]
    (from-poll delay poll)))

(defn interval [delay value]
  (from-poll delay #(next value)))

(defn from-array [values]
  (eventstream
    (fn [sink]
      (doseq [v values]
        (sink (next v)))
      (sink (end)))))
