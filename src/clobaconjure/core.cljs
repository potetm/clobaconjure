(ns clobaconjure.core
  (:refer-clojure :exclude [filter map merge next repeatedly take take-while])
  (:require [cljs.core :as c]))

(defprotocol ISubscribable
  (subscribe! [eventstream event]))

(def end #js ["<end>"])
(def more #js ["<more>"])

(defrecord EventStream [subscribe]
  ISubscribable
  (subscribe! [eventstream event]
    (subscribe event)))

(defn push [subscribers event]
  (doseq [[s i] (c/map vector @subscribers (iterate inc 0))
          :let [reply (s event)]]
    (when (= reply end)
      (swap! subscribers dissoc i))))

(defn- make-subscribe [subscribe-prev handler subscribers]
  (fn [sink]
    (swap! subscribers conj sink)
    (when (= (count @subscribers) 1)
      (subscribe-prev handler))))

(defn eventstream [subscribe]
  (let [subscribers (atom [])
        handler (partial push subscribers)]
    (->EventStream (make-subscribe subscribe handler subscribers))))

(defn- from-eventstream [es handler]
  (let [subscribers (atom [])]
    (->EventStream
      (make-subscribe
        (:subscribe es)
        (partial handler subscribers)
        subscribers))))

(defn filter [es f]
  (let [handler (fn [subscribers event]
                  (when (or (= event end) (f event))
                    (push subscribers event)))]
    (from-eventstream es handler)))

(defn map [es f]
  (let [handler (fn [subscribers event]
                  (push subscribers
                        (if (= event end)
                          event
                          (f event))))]
    (from-eventstream es handler)))

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

;; Unfortunately these are needed for testing until I can fix the js/setTimeout issues.
(subscribe! (later 1000 "hipsta!") #(js/console.log %))
(subscribe! (sequentially 1000 ["hipsta1" "hipsta2" "hipsta3"]) #(js/console.log %))
