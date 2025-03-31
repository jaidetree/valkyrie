(ns dev.jaide.valkyrie.core
  (:require
   [dev.jaide.valhalla.core :as v]
   [clojure.pprint :refer [pprint]]))

(defn create
  [id & {:keys [validate exhaustive atom-f] :as opts
         :or {validate true
              exhaustive false
              atom-f atom}}]
  (atom-f
   {::fsm        id
    :state       ::unset
    :transitions {}
    :validators  {:states {}
                  :actions {}}
    :opts        opts}))

(defn fsm?
  [fsm-ref]
  (contains? @fsm-ref ::fsm))

(defn assert-fsm
  [fsm-ref]
  (assert (fsm? fsm-ref) "Invalid FSM, missing ::fsm id"))

(defn register-state
  [fsm-ref id & [context-validator-map]]
  (assert-fsm fsm-ref)
  (assert (keyword? id) "State id value is required and must be a keyword")
  (assert (or (nil? context-validator-map)
              (map? context-validator-map) "Context is an optional hash-map"))
  (when (fn? (get-in @fsm-ref [:validators :states id]))
    (throw (js/Error. (str "State already defined " (pr-str id)))))
  (let [context-validator (if (nil? context-validator-map)
                            (v/literal {})
                            (v/record context-validator-map))
        validator (v/record {:value (v/literal id)
                             :context context-validator})]
    (swap! fsm-ref assoc-in [:validators :states id] validator)
    fsm-ref))

(defn- action-validator
  [id v-map]
  (v/record (-> {:type (v/literal id)
                 :meta (v/nilable (v/assert map?))}
                (into (when (map? v-map)
                        {:payload (v/record v-map)})))))

(defn register-action
  [fsm-ref id & [validator-map]]
  (assert-fsm fsm-ref)
  (assert (keyword? id) "Action id must be a keyword")
  (assert (or (map? validator-map)
              (nil? validator-map)) "Validator map must be nil or a hash-map of keys to validators")
  (when (fn? (get-in @fsm-ref [:validators :actions id]))
    (throw (js/Error. (str "Already defined action for " (pr-str id)))))
  (swap! fsm-ref assoc-in [:validators :actions id] (action-validator id validator-map))
  fsm-ref)

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

(defn register-transition
  [fsm-ref {:keys [states actions]} f]
  (assert-fsm fsm-ref)
  (let [fsm @fsm-ref
        transitions (pair-transitions fsm states actions)]
    (doseq [transition transitions]
      (when (fn? (get-in @fsm-ref [:transitions transition]))
        (throw (js/Error. (str "Transition already defined for state "
                               (pr-str (first transition))
                               " and action " (pr-str (second transition))))))
      (swap! fsm-ref
             assoc-in [:transitions transition] f))
    fsm-ref))

(defn init
  ([fsm-ref state]
   (init fsm-ref state {}))
  ([fsm-ref state context]
   (assert-fsm fsm-ref)
   (let [fsm @fsm-ref]
     (assert (= (:state fsm) ::unset) "FSM is already initialized")
     (if-let [validator (get-in fsm [:validators :states state])]
       (let [fsm (-> fsm
                     (assoc :state {:value state
                                    :context context}))
             result (v/validate validator (:state fsm))]
         (if (v/valid? result)
           (do
             (reset! fsm-ref fsm)
             fsm-ref)
           (throw (js/Error. (str "Invalid state\n" (with-out-str
                                                      (pprint (:errors result))))))))
       (throw (js/Error. (str "No validator found for state " (pr-str state))))))))

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

(defn transition
  [fsm-ref action]
  (let [fsm @fsm-ref
        action (assert-action fsm action)
        transition-fn (assert-transition fsm action)
        prev-state (:state fsm)
        new-state (-> prev-state
                      (merge (transition-fn prev-state action)))]
    (assert-state fsm new-state)
    (swap! fsm-ref assoc :state new-state)
    {:prev prev-state
     :next new-state
     :at (js/Date.now)}))


