(ns clobaconjure.core)

(defprotocol ISubscribable
  (subscribe [me sink]))

(def end #js {})

(defn later [delay value]
  (reify ISubscribable
    (subscribe [_ sink]
      (js/setTimeout
        (fn []
          (sink value)
          (sink end))
        delay))))

(defn sequentially [delay values]
  (reify ISubscribable
    (subscribe [_ sink]
      (js/setTimeout
        (fn []
          (sink (first values))
          (if (empty? values)
            (sink end)
            (sequentially delay (rest values))))))))
