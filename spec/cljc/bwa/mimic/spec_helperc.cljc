(ns bwa.mimic.spec-helperc
  (:require [c3kit.apron.log :as log]
            [speclj.core #?(:clj :refer :cljs :refer-macros) [around it]]))

(defn capture-logs-around []
  (around [it] (log/capture-logs (it))))
