(ns bwa.mimic.websocket
  (:require [c3kit.wire.js :as wjs]))

(defn ready-state [ws] (wjs/o-get ws "readyState"))
(defn send! [ws data] (js-invoke ws "send" data))

(defn close!
  ([ws] (js-invoke ws "close"))
  ([ws code] (js-invoke ws "close" code))
  ([ws code reason] (js-invoke ws "close" code reason)))

(defn connecting? [ws] (= 0 (ready-state ws)))
(defn open? [ws] (= 1 (ready-state ws)))
(defn closing? [ws] (= 2 (ready-state ws)))
(defn closed? [ws] (= 3 (ready-state ws)))
(defn dispatch-event! [ws e] (js-invoke ws "dispatchEvent" e))
