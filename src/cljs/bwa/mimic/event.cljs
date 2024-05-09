(ns bwa.mimic.event
  (:require [c3kit.wire.js :as wjs]))

;; TODO [BAC]: Could be better... will work for now
(def JsWebSocket js/WebSocket)
(defn- dispatch [ws] (when (isa? JsWebSocket (type ws)) :native))

;region API

(defmulti ->OpenEvent (fn [ws] (dispatch ws)))
(defmulti ->CloseEvent (fn [ws _code _reason _clean?] (dispatch ws)))
(defmulti ->MessageEvent (fn [ws _data] (dispatch ws)))
(defmulti ->ErrorEvent (fn [ws] (dispatch ws)))

;endregion

(defn- with-message-props [e sock data]
  (doto e
    (wjs/o-set "lastEventId" "")
    (wjs/o-set "data" data)
    (wjs/o-set "ports" [])
    (wjs/o-set "origin" (re-find #"^wss?://[^\/]*" (wjs/o-get sock "url")))))

(defn- with-close-props [e code reason clean?]
  (doto e
    (wjs/o-set "code" code)
    (wjs/o-set "reason" reason)
    (wjs/o-set "wasClean" clean?)))

;region Native Events

(defmethod ->MessageEvent :native [ws data]
  (-> (js/Event. "message")
      (with-message-props ws data)))

(defmethod ->OpenEvent :native [_ws] (js/Event. "open"))
(defmethod ->ErrorEvent :native [_ws] (js/Event. "error"))
(defmethod ->CloseEvent :native [_ws code reason clean?]
  (-> (js/Event. "close")
      (with-close-props code reason clean?)))

;endregion

;region Memory Events

(defn- ->MemEvent [element]
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

(defmethod ->OpenEvent :default [sock]
  (doto (->MemEvent sock)
    (wjs/o-set "type" "open")))

(defmethod ->CloseEvent :default [sock code reason clean?]
  (doto (->MemEvent sock)
    (wjs/o-set "type" "close")
    (with-close-props code reason clean?)))

(defmethod ->MessageEvent :default [sock data]
  (doto (->MemEvent sock)
    (wjs/o-set "type" "message")
    (with-message-props sock data)))

(defmethod ->ErrorEvent :default [sock]
  (doto (->MemEvent sock)
    (wjs/o-set "type" "error")))

;endregion
