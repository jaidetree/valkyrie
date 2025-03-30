(ns dev.jaide.valkyrie.core
  (:require
   [dev.jaide.valhalla.core :as v]
   [clojure.pprint :refer [pprint]]))

(defn create
  [& {:keys [validate exhaustive] :as opts
      :or {validate :true
           exhaustive false}}]
  {:state       ::unset
   :transitions {}
   :validators  {:states {}
                 :actions {}}
   :opts        opts})

(defn def-state
  [fsm id & [context-validator-map]]
  (assert (keyword? id) "State id value is required and must be a keyword")
  (assert (or (nil? context-validator-map)
              (map? context-validator-map) "Context is an optional hash-map"))
  (when (fn? (get-in fsm [:validators :states id]))
    (throw (js/Error. (str "State already defined " (pr-str id)))))
  (let [context-validator (if (nil? context-validator-map)
                            (v/literal {})
                            (v/record context-validator-map))
        validator (v/record {:value (v/literal id)
                             :context context-validator})]
    (-> fsm
        (assoc-in [:validators :states id] validator))))

(defn- action-validator
  [id v-map]
  (v/record (-> {:type (v/literal id)
                 :meta (v/nilable (v/assert map?))}
                (into (when (map? v-map)
                        {:payload (v/record v-map)})))))

(defn def-action
  [fsm id & [validator-map]]
  (assert (keyword? id) "Action id must be a keyword")
  (assert (or (map? validator-map)
              (nil? validator-map)) "Validator map must be nil or a hash-map of keys to validators")
  (when (fn? (get-in fsm [:validators :actions id]))
    (throw (js/Error. (str "Already defined action for " (pr-str id)))))
  (-> fsm
      (assoc-in [:validators :actions id] (action-validator id validator-map))))

(defn- pair-transitions
  [fsm states actions]
  (doseq [action actions]
    (when-not (fn? (get-in fsm [:validators :actions action]))
      (throw (js/Error. (str "Could not find validator for action " action)))))
  (doseq [state states]
    (when-not (fn? (get-in fsm [:validators :states state]))
      (throw (js/Error. (str "Could not find validator for state " state)))))
  (for [state states
        action actions]
    [state action]))

(defn def-transition
  [fsm {:keys [states actions]} f]
  (let [transitions (pair-transitions fsm states actions)]
    (loop [fsm fsm
           transitions transitions]
      (let [[transition & transitions] transitions]
        (when (fn? (get-in fsm [:transitions transition]))
          (throw (js/Error. (str "Transition already defined for state "
                                 (pr-str (first transition))
                                 " and action " (pr-str (second transition))))))
        (if (nil? transition)
          fsm
          (recur
           (assoc-in fsm [:transitions transition] f)
           transitions))))))

(defn init
  ([fsm state]
   (init fsm state {}))
  ([fsm state context]
   (assert (= (:state fsm) ::unset) "FSM is already initialized")
   (if-let [validator (get-in fsm [:validators :states state])]
     (let [fsm (-> fsm
                   (assoc :state {:value state
                                  :context context}))
           result (v/validate validator (:state fsm))]
       (if (v/valid? result)
         fsm
         (throw (js/Error. (str "Invalid state\n" (with-out-str
                                                    (pprint (:errors result))))))))
     (throw (js/Error. (str "No validator found for state " (pr-str state)))))))

(defn- assert-action
  [fsm action]
  (let [validator (get-in fsm [:validators :actions (:type action)])]
    (assert (fn? validator) (str "Action not defined, got " (pr-str action)))
    (-> (v/assert-valid validator action)
        (:output)
        (assoc-in [:meta :created-at] (js/Date.now)))))

(defn- assert-transition
  [fsm action]
  (let [state (get-in fsm [:state :value])
        transition (get-in fsm [:transitions [state (:type action)]])]
    (assert (fn? transition) (str "Transition not defined from state " (pr-str state)
                                  " from " (pr-str (:type action))))
    transition))

(defn- assert-state
  [fsm state]
  (let [validator (get-in fsm [:validators :states (:value state)])]
    (assert (fn? validator) (str "State not defined" (pr-str state)))
    (v/assert-valid validator state)))

(defn- abort
  [signal-controller]
  (.abort signal-controller))

(defn- transition-effect
  [{prev-effect :prev-effect :as prev-state} {next-effect :effect :as next-state}]
  (when prev-effect
    (abort (:signal prev-effect)))
  (if next-effect
    (let [controller (js/AbortController.)]
      (-> next-state
          (assoc-in [:effect :create] next-effect)
          (assoc-in [:effect :signal] controller)))
    next-state))

(defn transition
  [fsm action]
  (let [action (assert-action fsm action)
        transition-fn (assert-transition fsm action)
        prev-state (:state fsm)
        new-state (-> prev-state
                      (merge (transition-fn prev-state action)))]
    [(-> fsm
         (assoc :state new-state))
     {:prev prev-state
      :next new-state
      :at (js/Date.now)}]))


