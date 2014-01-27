(ns clobaconjure.core)

(defrecord EventStream [emitter sink subscribers])

(def end #js ["<end>"])
(def more #js ["<more>"])

(defn eventstream [emitter]
  (let [subscribers (atom [])
        sink (fn [event]
               (doseq [[s i] (map vector @subscribers (iterate inc 0))
                       :let [reply (s event)]]
                 (when (= reply end)
                   (swap! subscribers dissoc i)))
               (if (empty? @subscribers) end more))]
    (->EventStream emitter sink subscribers)))

(defn subscribe! [stream subscriber]
  (let [subscribers (:subscribers stream)]
    (swap! subscribers conj subscriber)
    (when (= (count @subscribers) 1)
      ((:emitter stream) (:sink stream)))))

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

;; Unfortunately these are needed for testing until I can fix the js/setTimeout issues.
(subscribe! (later 1000 "hipsta!") #(js/console.log %))
(subscribe! (sequentially 1000 ["hipsta1" "hipsta2" "hipsta3"]) #(js/console.log %))
