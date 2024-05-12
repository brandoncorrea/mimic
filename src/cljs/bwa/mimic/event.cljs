(ns bwa.mimic.event
  (:require [c3kit.wire.js :as wjs]))

(def JsWebSocket js/WebSocket)

;region API

;; TODO [BAC]: Could be better (maybe?)... will work for now
(defmulti ->OpenEvent (fn [ws] (type ws)))
(defmulti ->CloseEvent (fn [ws _code _reason _clean?] (type ws)))
(defmulti ->MessageEvent (fn [ws _data] (type ws)))
(defmulti ->ErrorEvent (fn [ws] (type ws)))

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

(defmethod ->MessageEvent JsWebSocket [ws data]
  (-> (js/Event. "message")
      (with-message-props ws data)))

(defmethod ->OpenEvent JsWebSocket [_ws] (js/Event. "open"))
(defmethod ->ErrorEvent JsWebSocket [_ws] (js/Event. "error"))
(defmethod ->CloseEvent JsWebSocket [_ws code reason clean?]
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
