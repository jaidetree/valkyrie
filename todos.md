# Todos

## 1. Getting Started

- [x] Create src/main/dev/jaide/valkyrie/core.cljs
- [x] Create src/test/dev/jaide/valkyrie/core_test.cljs
- [x] Create shadow-cljs.edn
- [x] Create package.json

## 2. Implement Defining a FSM Spec

- [ ] Implement `define` to create an atom
- [ ] Write tests for `define`
- [ ] Implement `state` to register a state with optional validators
- [ ] Write tests for `state`
- [ ] Implement `action` to register an action with optional validators
- [ ] Write tests for `action`
- [ ] Implement `effect` to register an effect with optional validators
- [ ] Write tests for `effect`
- [ ] Implement `transition` to register a transition with dynamic body
- [ ] Write tests for `transition`
- [ ] Update `transition` to support static transitions
- [ ] Implement tests for static `transition`
- [ ] Ensure all tests for defining a FSM spec are passing

## 3. Implement Reducers for Updating a FSM instance

- [ ] Implement `reduce-state*` reducer to calculate new state with value, ctx, and effect
  - [ ] Validate action
  - [ ] Check if transition is supported
  - [ ] If transition found but it's a keyword, update state value
  - [ ] If transition not found and exhaustive is enabled, throw error for unhandled state
  - [ ] If state returned is a keyword, update state value
  - [ ] If hash-map returned assume state, context, and optional effect
- [ ] Write tests for `reduce-state*` reducer
- [ ] Implement `run-effect!` helper
  - [ ] Provide
- [ ] Write tests for `run-effect!`
- [ ] Ensure all tests for reduce-state and run-effect! are passing

## 4. Implement IStateMachine Protocol

- [ ] Define `init` function
- [ ] Write docstr for `init`
- [ ] Define `dispatch` function
- [ ] Write docstr for `dispatch`
- [ ] Define `get` function
- [ ] Write docstr for `get`
- [ ] Define `subscribe` function
- [ ] Write docstr for `subscribe`
- [ ] Define `destroy` function
- [ ] Write docstr for `destroy`

## 5. Implement AtomFSM Using the IStateMachine Protocol

- [ ] Define AtomFSM defrecord
- [ ] Implement init
- [ ] Implement get
- [ ] Implement dispatch
- [ ] Implement subscribe
- [ ] Implement destroy
- [ ] Write tests for init
- [ ] Write tests for get
- [ ] Write tests for dispatch
- [ ] Write tests for subscribe
- [ ] Write tests for destroy

## 6. Prepare project for publishing

- [ ] Design logo
- [ ] Update readme.md
- [ ] Copy versioning script from valhalla
- [ ] Generate pom file
- [ ] Update version

## 7. Publish

- [ ] Add https://github.com/liquidz/build.edn
- [ ] Configure CI
- [ ] Draft a release
- [ ] Deploy to clojars
- [ ] Announce in Slack
