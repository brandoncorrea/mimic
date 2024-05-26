(ns bwa.mimic.spec-helperc
  #?(:cljs (:require-macros [bwa.mimic.spec-helperc :refer [capture-logs-around]]))
  (:require [c3kit.apron.log :as log]
            [taoensso.timbre :as timbre]
            [speclj.core #?(:clj :refer :cljs :refer-macros) [around]]))

;; TODO [BAC]: Fix in c3kit.apron.log/capture-logs instead of here
(defn capture-log!
  ([config level ?ns-str ?file ?line msg-type ?err vargs_ ?base-data]
   (capture-log! config level ?ns-str ?file ?line msg-type ?err vargs_ ?base-data nil nil))
  ([config level ?ns-str ?file ?line msg-type ?err vargs_ ?base-data callsite-id]
   (capture-log! config level ?ns-str ?file ?line msg-type ?err vargs_ ?base-data callsite-id nil))
  ([config level ?ns-str ?file ?line msg-type ?err vargs_ ?base-data callsite-id spying?]
   (swap! log/captured-logs conj [config level ?ns-str ?file ?line msg-type ?err vargs_ ?base-data callsite-id spying?])
   nil))

(defmacro capture-logs-around []
  `(around [it#]
     (let [original-level# (:min-level timbre/*config*)]
       (reset! log/captured-logs [])
       (try
         (timbre/set-min-level! :trace)
         (with-redefs [timbre/-log! capture-log!]
           (it#))
         (finally
           (timbre/set-min-level! original-level#))))))
