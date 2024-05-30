(ns bwa.mimic.spec-helperc
  #?(:cljs (:require-macros [bwa.mimic.spec-helperc :refer [capture-logs-around]]))
  (:require [c3kit.apron.log :as log]
            [speclj.core #?(:clj :refer :cljs :refer-macros) [around]]))

(defmacro capture-logs-around []
  `(around [it#] (log/capture-logs (it#))))
