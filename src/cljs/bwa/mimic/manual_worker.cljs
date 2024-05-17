(ns bwa.mimic.manual-worker
  "Time independent timeouts and intervals"
  (:require [c3kit.apron.corec :as ccc]
            [c3kit.apron.log :as log]))

(def id-pool (atom 0))
(def workers (atom {}))

(defn- workers-for-kind [kind] (ccc/find-by (vals @workers) :kind kind))

(defn- clear-worker! [id]
  (swap! workers dissoc id)
  nil)

(defn- find-worker [worker-or-id]
  (if (map? worker-or-id)
    worker-or-id
    (get @workers worker-or-id)))

;region Testing Interface

(defn intervals [] (workers-for-kind :interval))
(defn timeouts [] (workers-for-kind :timeout))
(defn clear! [] (reset! workers {}))

(defn tick! [worker-or-id]
  (let [{:keys [id kind code handler args]} (find-worker worker-or-id)]
    (when (= :timeout kind) (clear-worker! id))
    (if code
      (js/eval code)
      (apply handler args))))

;endregion

(defn- ->worker [kind id executable timeout args]
  (let [interval {:kind kind :id id :delay timeout}]
    (if (string? executable)
      (assoc interval :code executable)
      (cond-> (assoc interval :handler executable)
              (seq args) (assoc :args args)))))

(defn- create-kind! [kind executable timeout args]
  (let [id        (swap! id-pool inc)
        kind-name (if (= :timeout kind) "Timeout" "Interval")]
    (when-not (pos? timeout)
      (log/warn (str kind-name " created with a delay of " timeout ". This will execute on every 'tick'.")))
    (when (string? executable)
      (log/warn (str kind-name " created with a string instead of a function. eval will be executed.")))
    (swap! workers assoc id (->worker kind id executable timeout args))
    id))

(defn- create-interval!
  ([executable timeout] (create-interval! executable timeout nil))
  ([executable timeout args]
   (create-kind! :interval executable timeout args)))

(defn- create-timeout!
  ([executable timeout] (create-timeout! executable timeout nil))
  ([executable timeout args]
   (create-kind! :timeout executable timeout args)))

;region API

(defn set-interval
  ([code-or-fn] (create-interval! code-or-fn 0))
  ([code-or-fn timeout] (create-interval! code-or-fn timeout))
  ([code-or-fn timeout & args] (create-interval! code-or-fn timeout args)))

(defn clear-interval [id] (clear-worker! id))

(defn set-timeout
  ([code-or-fn] (create-timeout! code-or-fn 0))
  ([code-or-fn timeout] (create-timeout! code-or-fn timeout))
  ([code-or-fn timeout & args] (create-timeout! code-or-fn timeout args)))

(defn clear-timeout [id] (clear-worker! id))

;endregion
