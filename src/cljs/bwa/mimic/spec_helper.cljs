(ns bwa.mimic.spec-helper
  (:require [bwa.mimic.memory-server :as mem-server]
            [bwa.mimic.memory-websocket :as mem-ws]
            [bwa.mimic.server :as server]
            [c3kit.wire.js :as wjs]
            [speclj.core :refer-macros [before]]))

(defn with-websocket-impl [constructor]
  (before (set! js/WebSocket constructor)
          (reset! server/impl (mem-server/->MemServer))))

(defn with-memory-websockets []
  (with-websocket-impl mem-ws/->MemSocket))

(defn stub-performance-now [time]
  (before (wjs/o-set js/performance "now" (fn [] time))))
