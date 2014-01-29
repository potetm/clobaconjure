(ns clobaconjure.core
  (:refer-clojure :exclude [filter map merge next repeatedly take take-while])
  (:require [cljs.core :as c]))

(def end #js ["<end>"])
(def more #js ["<more>"])

(def end? (partial = end))

(defrecord Event [value
                  initial?
                  next?
                  end?
                  error?
                  has-value?])

(defn next [value]
  (map->Event {:value      value
               :initial?   false
               :next?      true
               :end?       false
               :error?     false
               :has-value? true}))

(defn initial [value]
  (map->Event {:value      value
               :initial?   true
               :next?      false
               :end?       false
               :error?     false
               :has-value? true}))

;; TODO: Come back and start using me!!!
(defn end-event [value]
  (map->Event {:value      nil
               :initial?   false
               :next?      false
               :end?       true
               :error?     false
               :has-value? false}))

(defn error [value]
  (map->Event {:value      nil
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
  (let [remove #(vec (concat (subvec %1 0 %2)
                             (subvec %1 (inc %2) (count %1))))]
    (doseq [[s i] (c/map vector @subscribers (iterate inc 0))
            :let [reply (s event)]]
      (when (end? reply)
        (swap! subscribers remove i)))))

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
                  (when (not (end? event))
                    (reset! current-value event))
                  (push subscribers event))
        subscribe (make-subscribe subscribe handler subscribers)
        my-subscribe (fn [sink]
                       (when @current-value
                         (sink @current-value))
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
                  (when (or (end? event) (f event))
                    (push subscribers event)))]
    (from-eventstream es handler)))

(defn map [es f]
  (let [handler (fn [subscribers event]
                  (push subscribers
                        (if (end? event)
                          event
                          (f event))))]
    (from-eventstream es handler)))

(defn take-while [es f]
  (let [handler (fn [subscribers event]
                  (if (or (end? event) (f event))
                    (push subscribers event)
                    (do
                      (push subscribers end)
                      end)))]
    (from-eventstream es handler)))

(defn merge [left right]
  (eventstream
    (fn [sink]
      (let [end-me? (atom false)
            smart-sink (fn [event]
                         (if (end? event)
                           (if @end-me?
                             (sink end)
                             (do
                               (reset! end-me? true)
                               more))
                           (sink event)))]
        (subscribe! left smart-sink)
        (subscribe! right smart-sink)))))

(defn later [delay value]
  (eventstream
    (fn [sink]
      (js/setTimeout
        (fn []
          (sink value)
          (sink end))
        delay))))

(defn sequentially [delay values]
  (letfn [(schedule
            [sink values]
            (js/setTimeout
              (fn []
                (if (empty? values)
                  (sink end)
                  (do
                    (sink (first values))
                    (schedule sink (rest values)))))
              delay))]
    (eventstream #(schedule % values))))

(defn from-array [values]
  (eventstream
    (fn [sink]
      (doseq [v values]
        (sink v))
      (sink end))))
