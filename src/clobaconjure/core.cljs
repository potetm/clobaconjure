(ns clobaconjure.core
  (:refer-clojure :exclude [filter map merge next repeatedly take take-while])
  (:require [cljs.core :as c]))

(def no-more #js ["<no-more>"])
(def more #js ["<more>"])

(def no-more? (partial = no-more))

(defn nop [])

(defn- set-interval [delay f]
  (js/setInterval f delay))

(defn- clear-interval [id]
  (js/clearInterval id))

(defn- set-timeout [delay f]
  (js/setTimeout f delay))

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
  (map-event [this f]
    (if has-value?
      (assoc this :value (f value))
      this)))

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

(defrecord EventStream [subscribe subscribers])

(defrecord Property [subscribe subscribers])

(defn subscribe! [obs subscriber]
  ((:subscribe obs) subscriber))

(defn subscribers [obs]
  @(:subscribers obs))

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

(defn- make-unsubscribe [unsubscribe-from-source sinks]
  (let [remove #(vec (remove = %))]
    (fn [sink]
      (swap! sinks remove)
      (when (empty? @sinks)
        (unsubscribe-from-source)))))

(defn- make-subscribe [subscribe-source handler sinks]
  (fn [sink]
    (swap! sinks conj sink)
    (make-unsubscribe
      (if (= (count @sinks) 1)
        (subscribe-source handler)
        nop)
      sinks)))

(defn eventstream [source]
  (let [subscribers (atom [])
        handler (partial push subscribers)]
    (->EventStream (make-subscribe source handler subscribers) subscribers)))

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
    (->Property my-subscribe subscribers)))

(defn to-property [es init-value]
  (property (:subscribe es) init-value))

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

(defn constant [value]
  (property
    (fn [sink]
      (sink (end)))
    value))

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

(defn- from-eventstream [es handler]
  (let [subscribers (atom [])]
    (->EventStream
      (make-subscribe
        (:subscribe es)
        (partial handler subscribers)
        subscribers)
      subscribers)))

(defn on-value! [es f]
  (subscribe!
    es
    (fn [event]
      (when (:has-value? event)
        (f (:value event))))))

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
