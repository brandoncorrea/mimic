(ns bwa.mimic.memory-websocket
  (:require [bwa.mimic.event :as event]
            [bwa.mimic.server :as server]
            [bwa.mimic.websocket :as ws]
            [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]
            [c3kit.wire.js :as wjs]))

(defn- encode-utf8
  "Encode a string in UTF-8"
  [s]
  (js-invoke (js/TextEncoder.) "encode" s))

(defn- syntax-error! [message data]
  (throw (js/SyntaxError. (str message ": " data))))

(defn- select-protocol [protocols]
  (let [protocols (js->clj protocols)]
    (cond (string? protocols) protocols
          (not (sequential? protocols))
          (syntax-error! "Protocols must be a string or a collection" protocols)
          (not= (count protocols) (count (distinct protocols)))
          (syntax-error! "Protocols may not contain duplicates" protocols)
          :else (or (first protocols) ""))))

(defn- assert-valid-url [url]
  (when-not (re-find #"^wss?://[^#]*$" url)
    (syntax-error! "URL is invalid" url)))

(defn- assert-short-reason [reason]
  (when (< 123 (wjs/o-get (encode-utf8 reason) "length"))
    (throw (ex-info "Reason must be less than 123 bytes" reason))))

(defn- assert-closure-code [code]
  (when-not (or (= 1000 code) (<= 3000 code 4999))
    (throw (ex-info "Code must be either 1000 or between 3000 and 4999" code))))

(defn- send [sock data]
  (case (ws/ready-state sock)
    0 (throw (ex-info "MemSocket is still in CONNECTING state." data))
    1 nil
    (log/error "MemSocket is already in CLOSING or CLOSED state.")))

(defn- on-close [sock code reason]
  (when-let [on-close (wjs/o-get sock "onclose")]
    (on-close (event/->CloseEvent sock code reason))))

(defn- close
  ([sock] (close sock 1000))
  ([sock code]
   (assert-closure-code code)
   (on-close sock code "")
   (wjs/o-set sock "readyState" 3)
   (log/info (str "Closing MemSocket with code: " code)))
  ([sock code reason]
   (assert-closure-code code)
   (assert-short-reason reason)
   (wjs/o-set sock "readyState" 3)
   (on-close sock code reason)
   (log/info (str "Closing MemSocket with code: " code))
   (log/info (str "Closing MemSocket with reason: " reason))))

(defn- init [url protocols]
  (assert-valid-url url)
  (js-obj
    "binaryType" "blob"
    "bufferedAmount" 0
    "extensions" ""
    "protocol" (select-protocol protocols)
    "readyState" 0
    "url" url))

(defn- append-listener [listeners listener event-name on-error]
  (fn [event]
    (when listeners (listeners event))
    (try
      (listener event)
      (catch :default e
        (on-error event)
        (log/error (str "Error occurred in MemSocket " event-name) e)))))

(defn- on-close-error [event] (wjs/o-set event "wasClean" false))
(def events #{"close" "message" "error" "open"})
(defn- add-listener [sock event listener]
  (when (events event)
    (let [error-handler (if (= "close" event) on-close-error ccc/noop)
          event         (str "on" event)]
      (wjs/o-update! sock event append-listener listener event error-handler))))

;; TODO [BAC]:
;;  - Event handlers need to be removable
;;  - When adding the same event handler multiple times how does WebSocket handle this?
;;    - Invoked once? Multiple times?
;;    - In what sequence is a handler invoked? Preserve the former? Overwrite with the latter?
(defn ->MemSocket
  ([url] (->MemSocket url (clj->js [])))
  ([url protocols]
   (let [sock (init url protocols)]
     (wjs/o-set sock "send" (partial send sock))
     (wjs/o-set sock "close" (partial close sock))
     (wjs/o-set sock "addEventListener" (partial add-listener sock))
     (wjs/o-set sock "onopen" ccc/noop)
     (wjs/o-set sock "onmessage" ccc/noop)
     (wjs/o-set sock "onerror" ccc/noop)
     (wjs/o-set sock "onclose" ccc/noop)
     (server/initiate sock)
     sock)))
