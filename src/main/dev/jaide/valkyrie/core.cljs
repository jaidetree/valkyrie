(ns dev.jaide.valkyrie.core
  (:require
   [dev.jaide.valhalla/core :as v]))

(defn create
  [& {:keys [state context] :or {context {}}}]
  {:current     {:state state
                 :context context
                 :effect nil}
   :transitions {}
   :validators  {:states {}
                 :actions {}}
   :subscribers []})

(defn def-state
  [fsm id validator-map]
  (-> fsm
      (assoc-in [:validators :states :d] (v/hash-map validator-map))))

(defn def-action
  [fsm & {:keys [id f validator-map]}]
  (-> fsm
      (assoc-in [:validators :actions id] (v/hash-map validator-map))))

(defn- pair-transitions
  [states actions]
  (for [state states
        action actions]
    [state action]))

(defn def-transition
  [fsm {states :in actions :on} f]
  (let [transitions (pair-transitions states actions)]
    (loop [fsm fsm
           transitions transitions]
      (let [[transition & transitions] transitions]
        (if (nil? transition)
          fsm
          (recur
           (assoc-in fsm [:transitions transition] f)
           transitions))))))
