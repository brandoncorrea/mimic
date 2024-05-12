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
    1 (server/receive sock data)
    (log/error "MemSocket is already in CLOSING or CLOSED state.")))

(defn- on-close [sock code reason]
  (ws/dispatch-event! sock (event/->CloseEvent sock code reason true)))

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

(defn- add-slash? [url] (not (re-find #"^wss?://[^/]*/" url)))
(defn- normalize-url [url] (cond-> url (add-slash? url) (str "/")))

(defn- init [url protocols]
  (assert-valid-url url)
  (let [url (normalize-url url)]
    (js-obj
      "binaryType" "blob"
      "bufferedAmount" 0
      "extensions" ""
      "protocol" (select-protocol protocols)
      "readyState" 0
      "url" url)))

(defn- add-listener [event-queue event listener]
  (when (not-any? #{listener} (get @event-queue event))
    (swap! event-queue update event ccc/conjv listener)))

(defn- remove-listener [event-queue event listener]
  (swap! event-queue update event #(ccc/removev #{listener} %)))

(defn- attempt-handler [event handler]
  (try
    (handler event)
    (catch :default e
      (let [event-type (wjs/o-get event "type")]
        (when (= "close" event-type) (wjs/o-set event "wasClean" false))
        (log/error (str "Error occurred in MemSocket " event-type) e)))))

(defn- dispatch-event [event-queue event]
  (let [event-type (wjs/o-get event "type")
        handlers   (seq (get @event-queue event-type))]
    (run! #(attempt-handler event %) handlers)))

(defn ->MemSocket
  ([url] (->MemSocket url (clj->js [])))
  ([url protocols]
   (let [sock        (init url protocols)
         event-queue (atom {})]
     (wjs/o-set sock "send" (partial send sock))
     (wjs/o-set sock "close" (partial close sock))
     (wjs/o-set sock "addEventListener" (partial add-listener event-queue))
     (wjs/o-set sock "removeEventListener" (partial remove-listener event-queue))
     (wjs/o-set sock "dispatchEvent" (partial dispatch-event event-queue))
     (server/initiate sock)
     sock)))
