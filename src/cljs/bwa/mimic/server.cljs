(ns bwa.mimic.server)

(def impl (atom nil))

(defmulti -connections (fn [server _sock] (:impl server)))
(defmulti -open (fn [server _sock] (:impl server)))
(defmulti -close (fn [server _sock] (:impl server)))
(defmulti -send (fn [server _sock _message] (:impl server)))
(defmulti -reject (fn [server _sock _code _reason] (:impl server)))
(defmulti -shutdown (fn [server] (:impl server)))
(defmulti -initiate (fn [server _sock] (:impl server)))

(defn connections
  "Returns a collection of active connections"
  [] (-connections @impl))

(defn initiate
  "The socket initiates a connection to the server."
  [sock] (-initiate @impl sock))

(defn open
  "The server accepts and opens a socket connection request.
   Invokes the socket onopen event and updates readyState to 1."
  [sock] (-open @impl sock))

(defn reject
  "The server rejects a socket connection request.
   Invokes the socket onerror, then onclose events and updates readyState to 3."
  [sock code reason] (-reject @impl sock code reason))

(defn close
  "The server closes a connection to the socket.
   Invokes the socket onclose event and updates readyState to 3."
  [sock] (-close @impl sock))

(defn send
  "The server sends a message to the socket.
   Invokes the socket onmessage event."
  [sock data] (-send @impl sock data))

(defn shutdown
  "The server shuts down.
   Invokes the socket onerror, then onclose events for all OPEN or CONNECTING sockets."
  [] (-shutdown @impl))

(def repl-options
  "Browser access with (set! js/Server repl-options)"
  (js-obj
    "initiate" initiate
    "open" open
    "reject" reject
    "close" close
    "send" send
    "shutdown" shutdown))

;region default

(defmethod -connections :default [_server _sock])
(defmethod -initiate :default [_server _sock])
(defmethod -open :default [_server _sock])
(defmethod -reject :default [_server _sock _code _reason])
(defmethod -close :default [_server _sock])
(defmethod -send :default [_server _sock _data])
(defmethod -shutdown :default [_server])

;endregion
