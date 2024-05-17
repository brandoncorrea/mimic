(ns bwa.mimic.spec-helper
  (:require [bwa.mimic.manual-worker :as worker]
            [bwa.mimic.memory-server :as mem-server]
            [bwa.mimic.memory-storage :as mem-store]
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

(defn- memory-storage-storage [js-store]
  (before
    (let [store (mem-store/->MemStorage)]
      (doseq [attr ["getItem" "setItem" "removeItem" "clear"]]
        (wjs/o-set js-store attr (wjs/o-get store attr))))))

(defn with-memory-local-storage []
  (memory-storage-storage js/localStorage))

(defn with-memory-session-storage []
  (memory-storage-storage js/sessionStorage))

(defn with-manual-worker []
  (before (worker/clear!)
          (set! js/setTimeout worker/set-timeout)
          (set! js/setInterval worker/set-interval)))
