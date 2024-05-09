(ns bwa.mimic.memory-server
  (:require [bwa.mimic.event :as event]
            [bwa.mimic.server :as server]
            [bwa.mimic.websocket :as ws]
            [c3kit.wire.js :as wjs]))

(defn ->MemoryServer []
  {:impl     :memory
   :running? (atom true)
   :sockets  (atom #{})})

(defn- assert-running! [server]
  (when-not @(:running? server)
    (throw (ex-info "Server is not running" server))))

(defn- assert-connecting! [sock]
  (when-not (ws/connecting? sock)
    (throw (ex-info "Socket is not CONNECTING" sock))))

(defn- assert-uninitialized [sockets sock]
  (when (contains? sockets sock)
    (throw (ex-info "Socket has already been initiated" sock))))

;; TODO [BAC]: onclose, onopen, onmessage and onerror do not work with real WebSockets.
;;  - addEventListener does not update the "onevent" properties,
;;    but rather is part of the WebSocket implementation.
;;  - Event handlers must be invoked with a js/Event object
;;  - A js/Event can be created, but not modified.
;;  - MemServer must only know about "dispatchEvent", and MemSocket must implement that instead of "onevent"s
;;  - Creation of Event objects must be tied to the WebSocket implementation being used

(defn- shutdown-socket! [sock]
  (wjs/o-set sock "readyState" 3)
  (ws/dispatch-event! sock (event/->ErrorEvent sock))
  (ws/dispatch-event! sock (event/->CloseEvent sock 1006 "" false)))

(defmethod server/-connections :memory [server]
  (vec @(:sockets server)))

(defmethod server/-initiate :memory [{:keys [sockets]} sock]
  (assert-connecting! sock)
  (assert-uninitialized @sockets sock)
  (swap! sockets conj sock)
  (wjs/add-listener sock "close" #(swap! sockets disj sock)))

(defmethod server/-open :memory [server sock]
  (assert-running! server)
  (assert-connecting! sock)
  (wjs/o-set sock "readyState" 1)
  (ws/dispatch-event! sock (event/->OpenEvent sock)))

(defmethod server/-close :memory [server sock]
  (assert-running! server)
  (when-not (#{0 1} (ws/ready-state sock))
    (throw (ex-info "Socket is not CONNECTING or OPEN" sock)))
  (wjs/o-set sock "readyState" 3)
  (ws/dispatch-event! sock (event/->CloseEvent sock 1000 "closed by server")))

(defmethod server/-reject :memory [server sock code reason]
  (assert-running! server)
  (assert-connecting! sock)
  (wjs/o-set sock "readyState" 3)
  (ws/dispatch-event! sock (event/->ErrorEvent sock))
  (ws/dispatch-event! sock (event/->CloseEvent sock code reason false)))

(defmethod server/-send :memory [server sock data]
  (assert-running! server)
  (when-not (ws/open? sock)
    (throw (ex-info "Socket is not OPEN" sock)))
  (ws/dispatch-event! sock (event/->MessageEvent sock data)))

(defmethod server/-shutdown :memory [{:keys [running? sockets] :as server}]
  (assert-running! server)
  (reset! running? false)
  (->> @sockets
       (filter (some-fn ws/open? ws/connecting?))
       (run! shutdown-socket!)))
