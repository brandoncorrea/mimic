(ns bwa.mimic.memory-storage
  "Fully substitutable for localStorage and sessionStorage"
  (:require [c3kit.wire.js :as wjs]))

(defn set-item [s k v] (js-invoke s "setItem" k v))
(defn get-item [s k] (js-invoke s "getItem" k))
(defn remove-item [s k] (js-invoke s "removeItem" k))
(defn clear [s] (js-invoke s "clear"))
(defn o-keys [obj] (js-invoke js/Object "keys" obj))
(defn o-entries [obj] (js-invoke js/Object "entries" obj))
(defn create-object [] (js-invoke js/Object "create" (js-obj)))
(def default-quota (* 5 1024 1024)) ; 5MB

;region Memory Storage

(defn- normalize [v]
  (cond
    (== js/undefined v) "undefined"
    (nil? v) "null"
    :else (js-invoke v "toString")))

(defn- with-kv-size [size [k v]] (+ size (count k) (count v)))
(defn- utf16-size [obj k v]
  (let [init (+ (count k) (count v))]
    (* 2 (reduce with-kv-size init (o-entries obj)))))

(defn- assert-under-quota! [obj quota k v]
  (let [size (utf16-size obj k v)]
    (when (> size quota)
      (throw
        (js/DOMException. (str "MemStorage quota has been exceeded. Quota: " quota ", Size: " size) "QuotaExceededError")))))

(defn- define-property [obj name prop]
  (js-invoke js/Object "defineProperty" obj name prop))

(defn- as-mutable [v]
  (js-obj
    "value" v
    "enumerable" true
    "writable" true
    "configurable" true))

(defn- as-immutable [v]
  (js-obj "value" v))

(defn- -set-item [obj quota k v]
  (let [k (normalize k)
        v (normalize v)]
    (assert-under-quota! obj quota k v)
    (define-property obj k (as-mutable v))))

(defn- -get-item [s k]
  (wjs/o-get s (normalize k)))

(defn- -remove-item [obj k]
  (js-delete obj (normalize k)))

(defn- -clear [obj]
  (run! #(remove-item obj %) (o-keys obj)))

(defn- define-immutable [obj name v]
  (define-property obj name (as-immutable v)))

(defn ->MemStorage
  ([] (->MemStorage default-quota))
  ([quota]
   (let [obj (create-object)]
     (doto obj
       (define-immutable "clear" #(-clear obj))
       (define-immutable "setItem" (partial -set-item obj quota))
       (define-immutable "getItem" (partial -get-item obj))
       (define-immutable "removeItem" (partial -remove-item obj))))))

;endregion
