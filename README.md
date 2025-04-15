# Valkyrie

<p align="center">
  <img src="https://raw.githubusercontent.com/jaidetree/valkyrie/main/resources/valkyrie-logo.png" alt="Valkyrie Logo" width="200"/>
</p>

A ClojureScript Finite State Machine library compatible with many state management tools. Valkyrie provides a robust way to model application state transitions with validation, side effects, and a clean API.

## Installation

### deps.edn / Babashka

```clojure
{:deps {dev.jaide/valkyrie {:mvn/version "2025.3.29"}}}
```

### Leiningen / Boot

```clojure
[dev.jaide/valkyrie "2025.3.29"]
```

## Usage

Valkyrie provides a simple yet powerful API for defining and using finite state machines in your ClojureScript applications.

### Defining a FSM Spec

First, create a specification for your state machine:

```clojure
(require '[dev.jaide.valkyrie.core :as fsm])

(def machine-spec (fsm/create :my-machine))
```

### Defining States

Define the valid states for your machine:

```clojure
(-> machine-spec
    (fsm/state :idle)
    (fsm/state :loading {:data any?})
    (fsm/state :success {:data any?})
    (fsm/state :error {:message string?}))
```

### Defining Actions

Define the actions that can trigger state transitions:

```clojure
(-> machine-spec
    (fsm/action :fetch {:url string?})
    (fsm/action :receive {:data any?})
    (fsm/action :fail {:message string?})
    (fsm/action :reset))
```

### Defining Effects

Define side effects that occur during state transitions:

```clojure
(fsm/effect machine-spec :fetch-data
  {:url string?}
  (fn [{:keys [effect dispatch]}]
    (js/fetch (:url effect)
      (fn [response]
        (dispatch {:type :receive :data response}))
      (fn [error]
        (dispatch {:type :fail :message (.-message error)})))
    ;; Return cleanup function (optional)
    (fn cleanup []
      (println "Cleaning up fetch effect"))))
```

### Defining Transitions

Define how states transition in response to actions:

```clojure
(-> machine-spec
    (fsm/transition
      {:from [:idle]
       :actions [:fetch]
       :to [:loading]}
      (fn [state action]
        {:value :loading
         :context {:data nil}
         :effect {:id :fetch-data
                  :url (:url action)}}))
    
    (fsm/transition
      {:from [:loading]
       :actions [:receive]
       :to [:success]}
      (fn [state action]
        {:value :success
         :context {:data (:data action)}}))
    
    (fsm/transition
      {:from [:loading]
       :actions [:fail]
       :to [:error]}
      (fn [state action]
        {:value :error
         :context {:message (:message action)}}))
    
    (fsm/transition
      {:from [:success :error]
       :actions [:reset]
       :to [:idle]}
      :idle))

;; Set initial state
(fsm/initial machine-spec :idle)
```

### Creating an Atom Instance

Create a state machine instance:

```clojure
(def machine (fsm/atom-fsm machine-spec {:state {:value :idle}}))
```

### Dispatching Actions

Trigger state transitions by dispatching actions:

```clojure
(fsm/dispatch machine {:type :fetch :url "https://api.example.com/data"})
```

### Reading State

Access the current state:

```clojure
;; Using deref
@machine
;; => {:value :loading, :context {:data nil}, :effect {:id :fetch-data, :url "..."}}

;; Using get
(get machine :value)
;; => :loading

;; Using get-in
(get-in machine [:context :data])
;; => nil
```

### Subscribing to State Changes

Listen for state transitions:

```clojure
(def unsubscribe 
  (fsm/subscribe machine 
    (fn [transition]
      (println "Transitioned from" (get-in transition [:prev :value])
               "to" (get-in transition [:next :value])
               "via" (get-in transition [:action :type])))))

;; Later, to stop listening:
(unsubscribe)
```

### Cleanup

Properly dispose of the machine when done:

```clojure
(fsm/destroy machine)
```

## Examples

### Simple Traffic Light

```clojure
(def traffic-light (fsm/create :traffic-light))

(-> traffic-light
    (fsm/state :red)
    (fsm/state :yellow)
    (fsm/state :green)
    
    (fsm/action :next)
    
    (fsm/transition
      {:from [:red] :actions [:next] :to [:green]}
      :green)
    
    (fsm/transition
      {:from [:green] :actions [:next] :to [:yellow]}
      :yellow)
    
    (fsm/transition
      {:from [:yellow] :actions [:next] :to [:red]}
      :red)
    
    (fsm/initial :red))

(def light (fsm/atom-fsm traffic-light {:state {:value :red}}))

;; Cycle through the lights
(fsm/dispatch light {:type :next}) ;; => green
(fsm/dispatch light {:type :next}) ;; => yellow
(fsm/dispatch light {:type :next}) ;; => red
```

### Async Data Fetcher

```clojure
(def fetcher (fsm/create :data-fetcher))

(-> fetcher
    (fsm/state :idle)
    (fsm/state :loading)
    (fsm/state :success {:data any?})
    (fsm/state :error {:message string?})
    
    (fsm/action :fetch {:url string?})
    (fsm/action :receive {:data any?})
    (fsm/action :fail {:message string?})
    (fsm/action :reset)
    
    (fsm/effect :fetch-data
      {:url string?}
      (fn [{:keys [effect dispatch]}]
        (-> (js/fetch (:url effect))
            (.then #(.json %))
            (.then #(dispatch {:type :receive :data %}))
            (.catch #(dispatch {:type :fail :message (.-message %)})))
        nil)) ;; No cleanup needed
    
    (fsm/transition
      {:from [:idle] :actions [:fetch] :to [:loading]}
      (fn [state action]
        {:value :loading
         :effect {:id :fetch-data :url (:url action)}}))
    
    (fsm/transition
      {:from [:loading] :actions [:receive] :to [:success]}
      (fn [state action]
        {:value :success
         :context {:data (:data action)}}))
    
    (fsm/transition
      {:from [:loading] :actions [:fail] :to [:error]}
      (fn [state action]
        {:value :error
         :context {:message (:message action)}}))
    
    (fsm/transition
      {:from [:success :error] :actions [:reset] :to [:idle]}
      :idle)
    
    (fsm/initial :idle))

(def data-fetcher (fsm/atom-fsm fetcher {:state {:value :idle}}))

;; Usage
(fsm/dispatch data-fetcher {:type :fetch :url "https://api.example.com/data"})
```

## Implementing Adapters

Valkyrie is designed to be adaptable to different state management systems. You can implement your own adapters by following the `IStateMachine` protocol:

### Init

Use `fsm/init` to validate and initialize a state:

```clojure
(fsm/init spec-atom state-map effect-map)
```

### Get

Implement `ILookup` and `IDeref` to allow state access:

```clojure
(-deref [this] ...)
(-lookup [this k] ...)
(-lookup [this k not-found] ...)
```

### Dispatch

Implement the `dispatch` method to handle actions:

```clojure
(dispatch [this action] ...)
```

### Subscribe

Implement the `subscribe` method to allow listeners:

```clojure
(subscribe [this listener] ...)
```

### Destroy

Implement the `destroy` method for cleanup:

```clojure
(destroy [this] ...)
```

## Visualizing State Machines

Valkyrie provides a way to generate Mermaid diagrams from your state machines:

```clojure
(println (fsm/spec->diagram machine-spec))
```

This will output a Mermaid flowchart that you can paste into documentation or a Mermaid-compatible viewer.

## License

Copyright © 2025 Jaide Tree

Distributed under the Eclipse Public License version 1.0.
