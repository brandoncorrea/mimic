(ns bwa.mimic.event
  (:require [c3kit.wire.js :as wjs]))

(defn ->Event [element]
  (js-obj
    "isTrusted" true
    "bubbles" false
    "cancelBubble" false
    "cancelable" false
    "composed" false
    "currentTarget" element
    "defaultPrevented" false
    "eventPhase" 0
    "returnValue" true
    "srcElement" element
    "target" element
    "timeStamp" (js-invoke js/performance "now")))

(defn ->OpenEvent [sock]
  (doto (->Event sock)
    (wjs/o-set "type" "open")))

(defn ->CloseEvent
  ([sock code reason] (->CloseEvent sock code reason true))
  ([sock code reason clean?]
   (doto (->Event sock)
     (wjs/o-set "code" code)
     (wjs/o-set "reason" reason)
     (wjs/o-set "wasClean" clean?)
     (wjs/o-set "type" "close"))))

(defn ->MessageEvent [sock data]
  (doto (->Event sock)
    (wjs/o-set "type" "message")
    (wjs/o-set "lastEventId" "")
    (wjs/o-set "data" data)
    (wjs/o-set "ports" [])
    (wjs/o-set "origin" (re-find #"^wss?://[^\/]*" (wjs/o-get sock "url")))))

(defn ->ErrorEvent [sock]
  (doto (->Event sock)
    (wjs/o-set "type" "error")))
