# Oreo

> [!WARNING]
> this is not even pre-alpha quality software, expect breakage!

# What is this?

Combines [Component](https://github.com/stuartsierra/component) and [Aero](https://github.com/juxt/aero) so that you can declaratively define your System, along with configuration of your Components in a single config.

> [!NOTE]
> I'm using Title-Case for Component the library, and lower-case component to mean the **actual** components in your system

# Installation

TBC

# Annotated example

```clojure
;; some assumptions:

; foobar.server/create expects a map of {:port :bind}
; foobar.widget/make expects a profile map

;; this would be your `config.edn` file somewhere in the classpath (resources or src)
{;; your shared configuration
 :profile #or [#env PROFILE "development"]
 :api {:server {:port #long #or [#env PORT 1002]
                :bind "0.0.0.0"}}

 ;; your system definition, it can be here or in a different file
 ;; merged by using #include reader macro, use #profile etc etc
 :oc/system {;; we're using namespace keys shorthand syntax for maps
             :web-server #:oc{;; where to find the function that creates the component
                              :init :foobar.server/create
                              ;; what is the configuration that :oc/init function
                              ;; expects
                              :config #ref [:api :server]

                              ;; component dependency list, optional
                              :using [:widget :uuid]}
             ;; functions can be components too!
             :uuid #:oc  #oc/ref :clojure.core/random-uuid

             ;; good old record-based component
             :widget #:oc {:init :foobar.widget/make
                           :config #ref [:profile]}}}


;; in your app/system.clj:

(ns app.system
  (:require
   [aero.core :as aero]
   [oreo.core :as oreo]
   [com.stuartsierra.component :as component]))

;; load configuration as usual
(def config
  (aero/read-config (io/resource "config.edn")))

;; fn to start the system
(defn start []
  (-> config
      ;; use ^^^ to expand into system map
      oreo/create-system
      ;; and start it
      component/start))
```

# How does this work?

It's **very simple** ;-) Oreo plugs into Areo's facilities and defines a couple of extra reader tags to simplify looking up vars which are used as component init functions, references to handlers etc.

In your config map (the one you load using `aero.core/read-config`) add `:oc/system` key with a map that defines your system. Name your component keys as you would normally, and define them as maps with special keys. They are:

- `:oc/init`  - a fully qualified, namespaced keyword or symbol referencing a function that creates your component, it will receive a single argument - the config map read from `:oc/config`.
- `:oc/config` - a configuration map for your component, either defined inline or using `#ref` reader macro or any other facility provided by Aero (`#env`, `#or` etc) used to produce a configuration map
- `:oc/using` - **optional** dependency list for the component, can be a vector or a map - just like Component expects it to. If missing, component will be constructed without any dependencies


Note that this is only required for Components which have a lifecycle and/or require dependency injection. If you want to add plain functions or even maps to your system, you can use two reader tags:

- `#oc/ref` - under the hood uses `requiring-resolve` to obtain a var (usually a function) specified as fully qualified namespaced keyword or a symbol
- `#oc/ref!` - under the hood uses `requiring-resolve` to obtain a var, but also derefs it to get actual var value (in cases where you need direct access to to it)

## Examples

Check `example` directory for more information as well as unit tests.


# Caveats, gotchas and Q&A

### Code reloading, renaming etc

Obviously using Oreo clashes with reloading your code, renaming things etc since everything is declarative. "Normal" usage of Component is not susceptible to this because your system is defined as part of your codebase. Just be careful about reloading things.


### I don't want to use records

That's not really my or Oreo's problem, but here's what you can do:

- use records
- your `:init` function can return something that implements the `Lifecycle` protocol using `extend-via-metadata` approach


### I don't want to define my config and system map in the same file

Use `#include` - see here for more information: https://github.com/juxt/aero#include


### So it's like [Integrant](https://github.com/weavejester/integrant)?

Maybe, I never used it but it looks vaguely similar. The reason why Oreo exists is because:

- Component already solved this problem, and because of it's reliance on protocols and records, it can be integrated (heh) with Java ecosystem in a more flexible way than Integrant
- Oreo doesn't reinvent what Areo does already - Integrant has its own notion of `ref` etc
- most importantly: Oreo is meant to help existing Component users, rather than require rewriting code

# Status/roadmap/TODO


- [x] make it work in a synthetic example
- [ ] finalize naming/API
- [ ] simplify function components
- [ ] see if any of `utility-belt.component` utils can be merged in and used
- [ ] somehow solve reloading issue - hook into `tools.namespace`?
- [ ] use qualified symbols for factory functions, binding handler functions etc by using `requiring-resolve, remove necessity for qualified keywords
- [ ] use in something real
- [ ] Clojars release
