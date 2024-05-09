(ns bwa.mimic.ws-interceptor
  (:require [bwa.mimic.server :as server]))

(def JsWebSocket js/WebSocket)
(defn- ->initiate [ws]
  (server/initiate ws)
  ws)

(defn ->WebSocketInterceptor
  ([url] (->initiate (JsWebSocket. url)))
  ([url protocols] (->initiate (JsWebSocket. url protocols))))
