# valkyrie

A ClojureScript Finite State Machine library compatible with many state management tools

# Overview

1. Logo + Intro
2. Installation
   1. clj.edn/babashka
   2. lein/boot
3. Usage
   1. Defining a fsm-spec
   2. Defining states
   3. Defining actions
   4. Defining effects
   5. Defining transitions
   6. Creating an atom instance
   7. Dispatching actions
   8. Reading state
   9. Subscribing
4. Example
   1. Simple traffic light
   2. Async fetcher
5. Implementing Adapters
   1. init
   2. get
   3. dispatch
   4. subscribe
   5. destroy
