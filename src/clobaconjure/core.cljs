(ns clobaconjure.core
  (:refer-clojure :exclude [filter map merge repeatedly take take-while])
  (:require [cljs.core :as c]
            [clobaconjure.event :as e]))

(enable-console-print!)

(def no-more #js ["<no-more>"])
(def more #js ["<more>"])

(def no-more? (partial = no-more))
(def more? (partial = more))

(defn nop [])

(defn- set-interval [delay f]
  (js/setInterval f delay))

(defn- clear-interval [id]
  (js/clearInterval id))

(defn- set-timeout [delay f]
  (js/setTimeout f delay))

(declare ^:private combine-properties)

(defrecord EventStream [subscribe subscribers])

(defprotocol IProperty
  (combine [left right f]))

(defrecord Property [subscribe subscribers]
  IProperty
  (combine [left right f]
    (let [combine-and-push
          (fn [val-left val-right sink event]
            (sink (e/apply-event event (f val-left val-right))))]
      (combine-properties left combine-and-push right combine-and-push))))

(defn subscribe! [obs subscriber]
  ((:subscribe obs) subscriber))

(defn subscribers [obs]
  @(:subscribers obs))

(defn- push [sinks event]
  {:pre [(:event? event)]}
  (let [to-remove (atom [])]
    (doseq [s @sinks
            :let [reply (s event)]]
      (when (or (no-more? reply))
        (swap! to-remove conj s)))
    (if (:end? event)
      (reset! sinks [])
      (doseq [s @to-remove]
        (swap! sinks #(vec (remove #{%2} %1)) s)))
    (if (seq @sinks)
      more
      no-more)))

(defn- make-unsubscribe [unsubscribe-from-source sink sinks]
  (let [remove #(vec (remove #{%2} %1))]
    (fn []
      (swap! sinks remove sink)
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
        sink
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

(defn- combine-sinks [out-sink my-val my-end? my-sink their-val their-end? unsubscribe!]
  (let [initial-sent (atom false)]
    (fn [event]
      (cond
        (:end? event) (do (reset! my-end? true)
                          (when (and @my-end? @their-end?)
                            (my-sink out-sink (e/end))
                            (unsubscribe!))
                          no-more)
        :default (do (reset! my-val (:value event))
                     (if (or (= @my-val ::none) (= @their-val ::none))
                       more
                       (if (and @initial-sent (:initial? event))
                         more
                         (do (reset! initial-sent true)
                             (let [reply (my-sink out-sink event)]
                               (when (no-more? reply)
                                 (unsubscribe!))
                               reply)))))))))

(defn- combine-properties [left sink-left right sink-right]
  (let [val-left (atom ::none)
        val-right (atom ::none)
        end-left? (atom false)
        end-right? (atom false)
        unsub-left (atom nop)
        unsub-right (atom nop)
        sink-left #(sink-left @val-left @val-right %1 %2)
        sink-right #(sink-right @val-left @val-right %1 %2)]
    (property
      (fn [sink]
        (let [unsub (fn [] (@unsub-left) (@unsub-right))
              sink-left (combine-sinks sink val-left end-left? sink-left val-right end-right? unsub)
              sink-right (combine-sinks sink val-right end-right? sink-right val-left end-left? unsub)]
          (reset! unsub-left (subscribe! left sink-left))
          (reset! unsub-right (subscribe! right sink-right))
          unsub)))))

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
      (sink (e/end))
      nop)))

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
