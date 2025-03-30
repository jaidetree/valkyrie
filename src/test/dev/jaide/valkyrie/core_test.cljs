(ns dev.jaide.valkyrie.core-test
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.test :refer [deftest testing is]]
   [dev.jaide.valkyrie.core :as fsm]
   [dev.jaide.valhalla.core :as v]))

(deftest create-test
  (testing "create"
    (testing "Returns a fsm hash-map"
      (let [fsm (fsm/create)]
        (is (map? fsm) "FSM was not a hash-map")
        (is (= (:state fsm) :dev.jaide.valkyrie.core/unset))))))

(deftest def-state-test
  (testing "def-state"
    (testing "without context"
      (let [fsm (fsm/create)
            fsm (fsm/def-state fsm :idle)
            validator (get-in fsm [:validators :states :idle])]
        (is (fn? validator))
        (is (v/valid? (v/validate validator {:value :idle
                                             :context {}})))))
    (testing "with context"
      (let [fsm (fsm/create)
            fsm (fsm/def-state fsm :pending {:url (v/string)})
            validator (get-in fsm [:validators :states :pending])]
        (is (fn? validator))
        (is (v/valid? (v/validate validator {:value :pending
                                             :context {:url "https://example.com"}})))))
    (testing "throws error if state already defined"
      (let [fsm (fsm/create)
            fsm (fsm/def-state fsm
                  :pending
                  {:url (v/string)})]
        (is (thrown? :default (fsm/def-state fsm :pending {:url (v/number)})))))))

(deftest def-action-test
  (testing "def-action"
    (testing "define action"
      (let [fsm (fsm/create)
            fsm (fsm/def-action fsm
                  :run-test
                  {:test-id (v/string)})
            validator (get-in fsm [:validators :actions :run-test])]
        (is (fn? validator))
        (is (v/valid? (v/validate validator {:type :run-test
                                             :payload {:test-id "this-test"}})))))
    (testing "throws error if already defined"
      (let [fsm (fsm/create)
            fsm (fsm/def-action fsm
                  :run-test
                  {:test-id (v/string)})]
        (is (thrown? :default (fsm/def-action fsm :run-test {:test-id (v/number)})))))))

(def test-fsm
  (-> (fsm/create)
      (fsm/def-state :green-light)
      (fsm/def-state :yellow-light)
      (fsm/def-state :red-light)
      (fsm/def-action :change-light)))

(deftest def-transition-test
  (testing "def-transition"
    (testing "define map of transitions"
      (let [fsm test-fsm
            fsm (fsm/def-transition
                  fsm
                  {:states [:green-light :yellow-light :red-light]
                   :actions [:change-light]}
                  (fn [{:keys [state _context _effect]} action]
                    (case [state action]
                      [:green-light :change-light] {:state :yellow-light}
                      [:yellow-light :change-light] {:state :red-light}
                      [:red-light :change-light] {:state :green-light})))]
        (is (fn? (get-in fsm [:transitions [:green-light :change-light]])))
        (is (fn? (get-in fsm [:transitions [:yellow-light :change-light]])))
        (is (fn? (get-in fsm [:transitions [:red-light :change-light]])))))

    (testing "throws error on overlapping defs"
      (let [fsm test-fsm
            fsm (fsm/def-transition
                  fsm
                  {:states [:green-light :yellow-light :red-light]
                   :actions [:change-light]}
                  (fn [{:keys [state _context _effect]} action]
                    (case [state action]
                      [:green-light :change-light] {:state :yellow-light}
                      [:yellow-light :change-light] {:state :red-light}
                      [:red-light :change-light] {:state :green-light})))]
        (is
         (thrown? :default
                  (fsm/def-transition
                    fsm
                    {:states [:yellow-light]
                     :actions [:change-light]}
                    (fn [{:keys [state _context _effect]} action]
                      {:state :red-light}))))))

    (testing "throws error if state was not defined"
      (let [fsm test-fsm
            fsm (fsm/def-transition
                  fsm
                  {:states [:green-light :yellow-light :red-light]
                   :actions [:change-light]}
                  (fn [{:keys [state _context _effect]} action]
                    (case [state action]
                      [:green-light :change-light] {:state :yellow-light}
                      [:yellow-light :change-light] {:state :red-light}
                      [:red-light :change-light] {:state :green-light})))]
        (is
         (thrown? :default
                  (fsm/def-transition
                    fsm
                    {:states [:purple-light]
                     :actions [:change-light]}
                    (fn [{:keys [state _context _effect]} action]
                      {:state :red-light}))))))

    (testing "throws error if action was not defined"
      (let [fsm test-fsm
            fsm (fsm/def-transition
                  fsm
                  {:states [:green-light :yellow-light :red-light]
                   :actions [:change-light]}
                  (fn [{:keys [state _context _effect]} action]
                    (case [state action]
                      [:green-light :change-light] {:state :yellow-light}
                      [:yellow-light :change-light] {:state :red-light}
                      [:red-light :change-light] {:state :green-light})))]
        (is
         (thrown? :default
                  (fsm/def-transition
                    fsm
                    {:states [:green-light :yellow-light :red-light]
                     :actions [:flash-light]}
                    (fn [{:keys [state _context _effect]} action]
                      {:state :red-light}))))))))

(deftest init-test
  (testing "init"
    (testing "Sets initial state on fsm"
      (let [fsm (fsm/init test-fsm :green-light)]
        (is (= (get-in fsm [:state :value])
               :green-light))
        (is (= (get-in fsm [:state :context])
               {}))))))


