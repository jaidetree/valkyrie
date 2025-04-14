(ns dev.jaide.valkyrie.core-test
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.test :refer [async deftest testing is]]
   [dev.jaide.valkyrie.core :as fsm]
   [dev.jaide.valhalla.core :as v]))

(deftest create-test
  (testing "create"
    (testing "Returns a fsm atom hash-map"
      (let [fsm @(fsm/create :test-fsm)]
        (is (map? fsm) "FSM was not a hash-map")))
    (testing "supports custom atom function"
      (let [fsm (fsm/create :test-fsm {:atom identity})]
        (is (map? fsm))))))

(deftest state-test
  (testing "state"
    (testing "without context"
      (let [fsm (fsm/create :test-fsm)
            fsm (fsm/state fsm :idle)
            validator (get-in @fsm [:validators :states :idle])]
        (is (fn? validator))
        (is (v/valid? (v/validate validator {:value :idle
                                             :context {}})))))
    (testing "with context"
      (let [fsm (fsm/create :test-fsm)
            fsm (fsm/state fsm :pending {:url (v/string)})
            validator (get-in @fsm [:validators :states :pending])]
        (is (fn? validator))
        (is (v/valid? (v/validate validator {:value :pending
                                             :context {:url "https://example.com"}})))))
    (testing "throws error if state already defined"
      (let [fsm (fsm/create :test-fsm)
            fsm (fsm/state fsm
                           :pending
                           {:url (v/string)})]
        (is (thrown? :default (fsm/state fsm :pending {:url (v/number)})))))))

(deftest register-action-test
  (testing "register-action"
    (testing "define action"
      (let [fsm (fsm/create :test-fsm)
            fsm (fsm/action fsm
                            :run-test
                            {:test-id (v/string)})
            validator (get-in @fsm [:validators :actions :run-test])]
        (is (fn? validator))
        (is (v/valid? (v/validate validator {:type :run-test
                                             :test-id "this-test"})))))
    (testing "throws error if already defined"
      (let [fsm (fsm/create :test-fsm)
            fsm (fsm/action fsm
                            :run-test
                            {:test-id (v/string)})]
        (is (thrown? :default (fsm/action fsm :run-test {:test-id (v/number)})))))))

(def test-fsm
  (fsm/create :test-fsm))

(fsm/state test-fsm :green-light)
(fsm/state test-fsm :yellow-light)
(fsm/state test-fsm :red-light)

(fsm/action test-fsm :change-light)

#_(def test-fsm
    (-> (fsm/create)
        (fsm/def-state :green-light)
        (fsm/def-state :yellow-light)
        (fsm/def-state :red-light)
        (fsm/def-action :change-light)))

(deftest register-transition-test
  (testing "register-transition"
    (testing "define map of transitions"
      (let [fsm test-fsm
            fsm (fsm/transition
                 fsm
                 {:states [:green-light :yellow-light :red-light]
                  :actions [:change-light]}
                 (fn [{:keys [state _context _effect]} action]
                   (case [state action]
                     [:green-light :change-light] {:state :yellow-light}
                     [:yellow-light :change-light] {:state :red-light}
                     [:red-light :change-light] {:state :green-light})))
            fsm @fsm]
        (is (fn? (get-in fsm [:transitions [:green-light :change-light]])))
        (is (fn? (get-in fsm [:transitions [:yellow-light :change-light]])))
        (is (fn? (get-in fsm [:transitions [:red-light :change-light]])))))

    (testing "throws error on overlapping defs"
      (let [fsm test-fsm]
        (is
         (thrown? :default
                  (fsm/transition
                   fsm
                   {:states [:yellow-light]
                    :actions [:change-light]}
                   (fn [{:keys [state _context _effect]} action]
                     {:state :red-light}))))))

    (testing "throws error if state was not defined"
      (let [fsm test-fsm]
        (is
         (thrown? :default
                  (fsm/transition
                   fsm
                   {:states [:purple-light]
                    :actions [:change-light]}
                   (fn [{:keys [state _context _effect]} action]
                     {:state :red-light}))))))

    (testing "throws error if action was not defined"
      (let [fsm test-fsm]
        (is
         (thrown? :default
                  (fsm/transition
                   fsm
                   {:states [:green-light :yellow-light :red-light]
                    :actions [:flash-light]}
                   (fn [{:keys [_state _context _effect]} _action]
                     {:state :red-light}))))))))

(deftest effect-test
  (testing "register-effect"
    (testing "Registers effect-handler without arg validator"
      (let [fsm (fsm/create :ctx-test-fsm)]
        (fsm/effect
         fsm :start-timer
         (fn [{:keys [_effect _state _action dispatch]}]
           (let [timer (js/setInterval #(dispatch {:type :tick
                                                   :timestamp (js/Date.now)}))]
             (fn cleanup-tmer
               []
               (js/clearInterval timer)))))
        (let [fsm @fsm
              validator (get-in fsm [:validators :effects :start-timer])]
          (is (fn? (get-in fsm [:effects :start-timer])))
          (is (fn? (get-in fsm [:validators :effects :start-timer])))
          (is (v/valid? (v/validate validator {:id :start-timer}))))))
    (testing "Registers effect-handler with arg validator"
      (let [fsm (fsm/create :ctx-test-fsm)]
        (fsm/effect
         fsm :fetch
         {:url (v/string)}
         (fn [_]
           nil))
        (let [fsm @fsm
              validator (get-in fsm [:validators :effects :fetch])]
          (is (fn? (get-in fsm [:effects :fetch])))
          (is (fn? (get-in fsm [:validators :effects :fetch])))
          (is (v/valid? (v/validate validator {:id :fetch :url "https://example.com"}))))))))

(defn create-fsm-with-ctx
  []
  (let [fsm (fsm/create :ctx-test-fsm)]
    (fsm/state fsm :idle)
    (fsm/state fsm :fulfilled {:data (v/assert #(do true))})
    (fsm/state fsm :pending {:url (v/string)})
    (fsm/action fsm :fetch {:url (v/string)})
    (fsm/action fsm :complete {:data (v/assert #(do true))})
    (fsm/effect fsm :fetch {:url (v/string)}
                (fn [{:keys [effect]}]
                  effect))
    (fsm/transition fsm
                    {:states [:idle]
                     :actions [:fetch]}
                    (fn [{:keys [_value _context]} action]
                      {:value :pending
                       :context {:url (:url action)}
                       :effect {:id :fetch :url (:url action)}}))
    (fsm/transition fsm
                    {:states [:pending]
                     :actions [:complete]}
                    (fn [state action]
                      {:value :fulfilled
                       :context (:data action)
                       :effect nil}))
    fsm))

(deftest init-test
  (testing "init"
    (testing "initializes a valid state"
      (let [fsm (create-fsm-with-ctx)
            state (fsm/init fsm :pending {:url "https://example.com"})]
        (is (= (:value state) :pending))
        (is (= (get-in state [:context :url]) "https://example.com"))))
    (testing "throws error if invalid context"
      (let [fsm (create-fsm-with-ctx)]
        (is (thrown? :default (fsm/init fsm :idle {:website "https://example.com"})))))
    (testing "throws error if invalid state"
      (let [fsm (create-fsm-with-ctx)]
        (is (thrown? :default (fsm/init fsm :idle {:url "https://example.com"})))))
    (testing "provides useful error message"
      (let [fsm (create-fsm-with-ctx)
            error (try (fsm/init fsm :pending {:url :url})
                       (catch :default err
                         (.-message err)))]
        (is (= error
               "Invalid state\ncontext.url: Expected string, got :url"))))))

(deftest atom-fsm-test
  (testing "atom-fsm"
    (testing "Wraps an atom that implements deref"
      (let [fsm (fsm/atom-fsm
                 (create-fsm-with-ctx)
                 {:state :idle})
            state @fsm]
        (is (= (:value state) :idle))
        (is (= (:context state) {}))
        (is (= (:effect state) nil))))))

(deftest get-state-test
  (testing "get-state"
    (testing "Returns full internal state"
      (let [fsm (fsm/atom-fsm
                 (create-fsm-with-ctx)
                 {:state :idle})
            state (fsm/get-state fsm)]
        (is (= (-> state :state :value) :idle))
        (is (= (-> state :state :context) {}))
        (is (= (-> state :state :effect) nil))))))

(deftest dispatch-test
  (testing "dispatch"
    (testing "defined transitions return a valid state"
      (let [fsm (fsm/atom-fsm
                 (create-fsm-with-ctx)
                 {:state :idle})
            _ (fsm/dispatch fsm {:type :fetch :url "https://example.com"})
            state @fsm]
        (is (= (:value state) :pending))
        (is (= (:context state) {:url "https://example.com"}))
        (is (= (:effect state) {:id :fetch :url "https://example.com"}))))))

(deftest subscribe-test
  (testing "subscribe"
    (testing "dispatched events are broadcast to subscribers"
      (let [transaction (atom {})
            fsm (fsm/atom-fsm
                 (create-fsm-with-ctx)
                 {:state :idle})]
        (fsm/subscribe
         fsm
         (fn [tx]
           (reset! transaction tx)))
        (fsm/dispatch fsm {:type :fetch :url "https://example.com"})
        (let [{:keys [prev next action]} @transaction]
          (is (= (:type action) :fetch))
          (is (= (:url action) "https://example.com"))
          (is (= (:value prev) :idle))
          (is (= (:context prev) {}))
          (is (= (:effect prev) nil))
          (is (= (:value next) :pending))
          (is (= (:context next) {:url "https://example.com"}))
          (is (= (:effect next)  {:id :fetch :url "https://example.com"})))))))

(deftest unsubscribe-test
  (testing "unsubscribe"
    (testing "removes subscription from fsm"
      (let [transaction (atom {})
            fsm (fsm/atom-fsm
                 (create-fsm-with-ctx)
                 {:state :idle})
            unsubscribe (fsm/subscribe
                         fsm
                         (fn [tx]
                           (reset! transaction tx)))]
        (fsm/dispatch fsm {:type :fetch :url "https://example.com"})
        (unsubscribe)
        (fsm/dispatch fsm {:type :complete :data {}})
        (let [{:keys [prev next action]} @transaction]
          (is (= (:type action) :fetch))
          (is (= (:url action) "https://example.com"))
          (is (= (:value prev) :idle))
          (is (= (:context prev) {}))
          (is (= (:effect prev) nil))
          (is (= (:value next) :pending))
          (is (= (:context next) {:url "https://example.com"}))
          (is (= (:effect next)  {:id :fetch :url "https://example.com"})))))))

(defn create-counter-fsm
  []
  (let [fsm (fsm/create :ctx-counter-fsm)]
    (fsm/state fsm :active {:num (v/number)})
    (fsm/state fsm :completed {:num (v/number)})
    (fsm/action fsm :increment)
    (fsm/action fsm :complete)
    (fsm/effect fsm :increment-again
                (fn [{:keys [dispatch]}]
                  (dispatch {:type :increment})
                  (dispatch {:type :complete})))
    (fsm/transition fsm
                    {:states [:active]
                     :actions [:increment]}
                    (fn [state _action]
                      {:value :active
                       :context {:num (inc (get-in state [:context :num]))}
                       :effect {:id :increment-again}}))
    (fsm/transition fsm
                    {:states [:active]
                     :actions [:complete]}
                    (fn [state _action]
                      {:value :completed
                       :context {:num (get-in state [:context :num])}
                       :effect nil}))
    fsm))

(deftest run-effect-test
  (testing "side-effects can run and dispatch multiple actions"
    (let [fsm (fsm/atom-fsm
               (create-counter-fsm)
               {:state :active
                :context {:num 0}})
          promise (js/Promise.
                   (fn [resolve]
                     (fsm/subscribe fsm
                                    (fn [{:keys [next]}]
                                      (when (= (:value next) :completed)
                                        (resolve next))))))]
      (fsm/dispatch fsm {:type :increment})
      (async done
             (-> promise
                 (.then (fn [state]
                          (is (= (:value state) :completed))
                          (is (= (:context state) {:num 2}))
                          (is (= (:effect state)  nil))
                          (done))))))))

(comment
  #_(def test-fsm
      (-> (fsm/create)
          (fsm/def-state :green-light)
          (fsm/def-state :yellow-light)
          (fsm/def-state :red-light)
          (fsm/def-action :change-light)

          (fsm/def-transition
            {:states [:green-light :yellow-light :red-light]
             :actions [:change-light]}
            (fn [{:keys [state _context _effect]} action]
              (case [state action]
                [:green-light :change-light] {:state :yellow-light
                                              :effect (fn [{:keys [dispatch signal]}]
                                                        (let [timer (js/setTimeout
                                                                     #(dispatch {:type :change-light})
                                                                     (* 2 60))]
                                                          (on-abort signal
                                                                    (js/clearTimeout timer))))}
                [:yellow-light :change-light] {:state :red-light
                                               #_#_:effect ...}
                [:red-light :change-light] {:state :green-light
                                            #_#_:effect ...}))))))
