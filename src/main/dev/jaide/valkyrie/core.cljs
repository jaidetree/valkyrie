(ns dev.jaide.valkyrie.core
  (:refer-clojure :exclude [])
  (:require
   [clojure.core :as cc]
   [cljs.core :refer [IDeref ILookup]]
   [dev.jaide.valhalla.core :as v]
   [clojure.pprint :refer [pprint]]))

(defn create
  [id & {:keys [atom] :as opts
         :or {atom atom}}]
  (atom
   {:fsm        id
    :transitions {}
    :cleanup-effect nil
    :effects     {}
    :validators  {:states {}
                  :actions {}
                  :effects {}}
    :opts        opts}))

(defn fsm?
  [fsm-spec-ref]
  (contains? @fsm-spec-ref :fsm))

(defn assert-fsm-spec
  [fsm-spec-ref]
  (assert (fsm? fsm-spec-ref) "Invalid FSM, missing :fsm id"))

(defn state
  [fsm-spec-ref id & [context-validator-map]]
  (assert-fsm-spec fsm-spec-ref)
  (assert (keyword? id) "State id value is required and must be a keyword")
  (assert (or (nil? context-validator-map)
              (map? context-validator-map) "Context is an optional hash-map"))
  (when (fn? (get-in @fsm-spec-ref [:validators :states id]))
    (throw (js/Error. (str "State already defined " (pr-str id)))))
  (let [context-validator (if (nil? context-validator-map)
                            (v/literal {})
                            (v/record context-validator-map))
        validator (v/record {:value (v/literal id)
                             :context context-validator})]
    (swap! fsm-spec-ref assoc-in [:validators :states id] validator)
    fsm-spec-ref))

(defn- action-validator
  [id v-map]
  (v/record (-> {:type (v/literal id)}
                (merge (when (map? v-map)
                         v-map)))))

(defn action
  [fsm-spec-ref id & [validator-map]]
  (assert-fsm-spec fsm-spec-ref)
  (assert (keyword? id) "Action id must be a keyword")
  (assert (or (map? validator-map)
              (nil? validator-map)) "Validator map must be nil or a hash-map of keys to validators")
  (when (fn? (get-in @fsm-spec-ref [:validators :actions id]))
    (throw (js/Error. (str "Already defined action for " (pr-str id)))))
  (swap! fsm-spec-ref assoc-in [:validators :actions id] (action-validator id validator-map))
  fsm-spec-ref)

(defn- effect-validator
  [id v-map]
  (v/record (-> {:id (v/literal id)}
                (merge (when (map? v-map)
                         v-map)))))

(defn effect
  ([fsm-spec-ref id handler]
   (effect fsm-spec-ref id nil handler))
  ([fsm-spec-ref id validator-map handler]
   (assert (keyword? id) "Effect id must be a keyword")
   (assert (or (map? validator-map)
               (nil? validator-map))
           "Validator map must be nil or a hash-map of keys to validators")
   (assert (fn? handler) "Effect handler must be a function")
   (when (fn? (get-in @fsm-spec-ref [:validators :effects id]))
     (throw (js/Error. (str "Effect already defined for " (pr-str id)))))
   (swap! fsm-spec-ref assoc-in [:validators :effects id] (effect-validator id validator-map))
   (swap! fsm-spec-ref assoc-in [:effects id] handler)
   fsm-spec-ref))

(defn- transitions-map->kvs
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

(defn transition
  [fsm-spec-ref {:keys [states actions]} f-or-kw]
  (assert-fsm-spec fsm-spec-ref)
  (let [fsm @fsm-spec-ref
        transitions (transitions-map->kvs fsm states actions)]
    (doseq [transition transitions]
      (when (fn? (get-in @fsm-spec-ref [:transitions transition]))
        (throw (js/Error. (str "Transition already defined for state "
                               (pr-str (first transition))
                               " and action " (pr-str (second transition))))))
      (swap! fsm-spec-ref
             assoc-in [:transitions transition] f-or-kw))
    fsm-spec-ref))

(defn assert-state
  [fsm-spec state]
  (if-let [validator (get-in fsm-spec [:validators :states (:value state)])]
    (let [result (v/validate validator state)]
      (if (v/valid? result)
        (:output result)
        (throw (js/Error.
                (str "Invalid state\n"
                     (v/errors->string (:errors result)))))))

    (throw (js/Error. (str "FSM Init: Validator not found for state, got "
                           (pr-str state))))))

(defn assert-effect
  [fsm-spec effect]
  (if (nil? effect)
    effect
    (if-let [validator (get-in fsm-spec [:validators :effects (:id effect)])]
      (let [result (v/validate validator effect)]
        (if (v/valid? result)
          (:output result)
          (throw (js/Error. (str "FSMInvalidEffectError: Effect is not valid\n"
                                 (v/errors->string (:errors result)))))))
      (throw (js/Error. (str "FSMInvalidEffectError: Validator not found for effect, got "
                             (pr-str effect)))))))

(defn init
  [fsm-spec-ref state & [context effect]]
  (assert-fsm-spec fsm-spec-ref)
  (let [fsm-spec  @fsm-spec-ref
        state {:value state
               :context context
               :effect effect}]
    (assert-state fsm-spec state)
    (assert-effect fsm-spec effect)
    state))

(defn- assert-action
  [fsm action]
  (let [validator (get-in fsm [:validators :actions (:type action)])]
    (assert (fn? validator) (str "Action not defined, got " (pr-str action)))
    action))

(defn- get-transition-fn
  [fsm state action]
  (let [transition (get-in fsm [:transitions [state (:type action)]])]
    (assert (fn? transition) (str "Transition not defined from state " (pr-str state)
                                  " to " (pr-str (:type action))))
    transition))

(defn create-transition
  [fsm-spec-ref prev-state action]
  (let [fsm-spec @fsm-spec-ref
        action (assert-action fsm-spec action)
        transition-fn (get-transition-fn fsm-spec (:value prev-state) action)
        next-state (-> prev-state
                       (merge (transition-fn prev-state action)))]
    {:prev prev-state
     :next next-state
     :action action
     :at (js/Date.now)}))

(defn reduce-state
  [fsm-spec-ref prev-state action]
  (let [spec @fsm-spec-ref
        action (-> (assert-action spec action)
                   (assoc-in [:meta :created-at] (js/Date.now)))
        transition (create-transition fsm-spec-ref prev-state action)]
    (assert-state spec (:next transition))
    transition))

(defprotocol IStateMachine
  "A protocol for defining state machines against a spec atom. Supports creating
  adapters for different state systems such as streams, reagent atoms, or
  other state management libraries.

  It is also important to implement cljs.core/IDeref -deref and 
  cljs.core/ILookup -lookup methods to add support for deref syntax and common
  get and get-in functions.
  "
  (internal-state
    [machine]
    "Intended for internal use or debugging.
    
    Arguments:
    - fsm - Instance of a FSM implementing the IStateMachine protocol
    
    Returns internal state hash-map including the state, cleanup-effect,
    and subscriptions")
  (dispatch
    [machine action]
    "Dispatch actions to invoke defined transitions between states.
    States returned are validated against the fsm spec this machine was
    created against.

    Arguments:
    - fsm - Instance of a FSM implementing the IStateMachine protocol
    - action - Hash-map with a :type and other types matching what an fsm spec
               action

    Returns a transition hash-map with :next, :prev, and :action
    ")
  (subscribe
    [machine listener]
    "Add a listener function to receive transition hash-maps
    
    Arguments:
    - fsm - Instance of a FSM implementing the IStateMachine protocol
    - listener - A function that accepts transition hash-maps
    
    Returns a function to unsubscribe the listener from future transitions")
  (destroy
    [machine]
    "Remove all subscriptions, clears any stored state, and stops any running 
     effects.
    
    Arguments:
    - fsm - Instance of a FSM implementing the IStateMachine protocol
    
    Returns nil"))

(defn run-effect!
  [fsm-spec-ref fsm transition]
  (let [cleanup-effect (get fsm :cleanup-effect)
        prev-effect (get-in transition [:prev :effect])
        next-effect (get-in transition [:next :effect])]
    (cond
      (= prev-effect next-effect)
      [:unchanged cleanup-effect]

      (and (nil? next-effect) (fn? cleanup-effect))
      (do
        (cleanup-effect)
        [:updated nil])

      (nil? next-effect)
      [:updated nil]

      :else
      (do
        (when (fn? cleanup-effect)
          (cleanup-effect))
        (let [spec @fsm-spec-ref
              effect-validator (get-in spec [:validators :effects (:id next-effect)])
              effect-fn (get-in spec [:effects (:id next-effect)])]
          (assert (fn? effect-validator) (str "Effect undefined, got " (pr-str next-effect)))
          (let [result (v/validate effect-validator next-effect)]
            (if (v/valid? result)
              [:updated (effect-fn {:fsm fsm
                                    :state (:next transition)
                                    :action (:action transition)
                                    :effect next-effect
                                    :dispatch #(dispatch fsm %)})]
              (throw (js/Error. (str "Invalid effect " (v/errors->string (:errors result))))))))))))

(defn destroyed?
  [fsm]
  (= (get @fsm :value) ::destroyed))

(defn assert-alive
  [fsm]
  (assert (not (destroyed? fsm)) "Cannot proceed, this FSM was destroyed"))

(deftype AtomFSM [spec-atom state-atom]
  IStateMachine

  (internal-state [this]
    (assert-alive this)
    @state-atom)

  (dispatch [this action]
    (assert-alive this)
    (let [state @this
          transition (reduce-state spec-atom state action)]
      (swap! state-atom update :state merge (:next transition))
      (doseq [subscriber (get @state-atom :subscribers)]
        (subscriber transition))
      (swap! state-atom (fn [state]
                          (let [[status cleanup-effect] (run-effect! spec-atom this transition)]
                            (case status
                              :updated (assoc state :cleanup-effect (when (fn? cleanup-effect)
                                                                      cleanup-effect))
                              state))))
      transition))

  (subscribe [this listener]
    (assert-alive this)
    (swap! state-atom update :subscribers conj listener)
    (fn unsubscribe
      []
      (swap! state-atom update :subscribers disj listener)))

  (destroy [this]
    (assert-alive this)
    (when-let [cleanup-effect (get @state-atom :cleanup-effect)]
      (println "cleanup" cleanup-effect)
      (cleanup-effect))
    (swap! state-atom merge {:state {:value ::destroyed
                                     :context {}
                                     :effect ::destroyed}
                             :cleanup-effect nil
                             :subscribers #{}})
    this)

  IDeref
  (-deref [_this]
    (:state @state-atom))

  ILookup
  (-lookup [this k]
    (-lookup this k nil))
  (-lookup [this k not-found]
    (get @this k not-found)))

(defn atom-fsm
  "Create an FSM instance from a spec with an initial state based on an atom.

  Notes:
  - Every transition must be validated
  - Returned FSM can be derefed @fsm as well as (get fsm :value)

  Arguments:
  - spec - An FSM spec atom created with the `fsm/create` function
  - opts - Required hashmap of named options

  Options:
  - state - Initial state value keyword
  - context - Optional default context if initial state includes context

  Returns an instance of FSMAtom
  "
  [spec {:keys [state context effect atom]
         :or {atom atom
              context {}}}]
  (AtomFSM. spec
            (atom {:state (init spec state context effect)
                   :cleanup-effect nil
                   :subscribers #{}})))

(comment
  (let [xs #{:a :b :c}]
    (disj xs :b)
    (conj xs :d)))
