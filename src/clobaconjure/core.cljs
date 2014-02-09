(ns clobaconjure.core
  (:refer-clojure :exclude [filter map merge repeatedly take take-while])
  (:require [cljs.core :as c]
            [clobaconjure.event :as e]))

(enable-console-print!)

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

(defn- make-subscribe [source handler sinks]
  (let [ended? (atom false)
        handler (fn [event]
                  (let [my-end? @ended?]
                    (when (:end? event)
                      (reset! ended? true))
                    (if my-end?
                      (handler (e/end))
                      (handler event))))]
    (fn [sink]
      (swap! sinks conj sink)
      (make-unsubscribe
        (if (= (count @sinks) 1)
          (source handler)
          nop)
        sinks))))

(defn eventstream [source]
  (let [subscribers (atom [])
        handler (partial push subscribers)]
    (->EventStream (make-subscribe source handler subscribers) subscribers)))

(defn property [source]
  (let [current-value (atom ::none)
        subscribers (atom [])
        handler (fn [event]
                  (when-not (:end? event)
                    (reset! current-value (:value event)))
                  (push subscribers event))
        subscribe (make-subscribe source handler subscribers)
        my-subscribe (fn [sink]
                       (when-not (= ::none @current-value)
                         (sink (e/initial @current-value)))
                       (subscribe sink))]
    (->Property my-subscribe subscribers)))

(defn to-property [es]
  (property (:subscribe es)))

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
                       (e/next value))
                   (e/end))))]
    (from-poll delay poll)))

(defn later [delay value]
  (sequentially delay [value]))

(defn repeatedly [delay values]
  (let [values (atom values)
        poll (fn []
               (let [val (first @values)]
                 (swap! values #(conj (vec (rest %)) val))
                 (e/next val)))]
    (from-poll delay poll)))

(defn interval [delay value]
  (from-poll delay #(e/next value)))

(defn from-array [values]
  (eventstream
    (fn [sink]
      (doseq [v values]
        (sink (e/next v)))
      (sink (e/end)))))

(defn constant [value]
  (property
    (fn [sink]
      (sink (e/initial value))
      (sink (e/end)))))

(defn merge [left right]
  (eventstream
    (fn [sink]
      (let [end-me? (atom false)
            smart-sink (fn [event]
                         (if (:end? event)
                           (if @end-me?
                             (sink (e/end))
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

(defn filter [es pred]
  (let [handler (fn [sinks event]
                  (if (or (:end? event) (pred (:value event)))
                    (push sinks event)
                    more))]
    (from-eventstream es handler)))

(defn map [es f]
  (let [handler (fn [sinks event]
                  (push sinks (e/map-event event f)))]
    (from-eventstream es handler)))

(defn take-while [es pred]
  (let [handler (fn [sinks event]
                  (if (or (:end? event) (pred (:value event)))
                    (push sinks event)
                    (do
                      (push sinks (e/end))
                      no-more)))]
    (from-eventstream es handler)))

(defn take [es num]
  (let [n (atom num)
        handler (fn [sinks event]
                  (if (or (:end? event) (>= (swap! n dec) 0))
                    (push sinks event)
                    (do (push sinks (e/end))
                        no-more)))]
    (from-eventstream es handler)))
