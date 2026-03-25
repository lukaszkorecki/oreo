# Oreo

Oreo combines [Component](https://github.com/stuartsierra/component) and [Aero](https://github.com/juxt/aero), giving you
the ability to declaratively define your system — along with the configuration of your components — in a single `config.edn` file.

> [!NOTE]
> I'm using Title-Case for the Component library and lower-case for a component to mean the **actual** components in your system.

# Installation

- `deps.edn` - bleeding edge:

```clojure
lukaszkorecki/oreo {:git/url "https://github.com/lukaszkorecki/oreo.git"
                    :git/sha "<SHA>"}
```

- stable releases:

[![Clojars Project](https://img.shields.io/clojars/v/org.clojars.lukaszkorecki/oreo.svg)](https://clojars.org/org.clojars.lukaszkorecki/oreo)


# How does this work?

Oreo plugs into Aero's reader tag system and defines two extra tags (`#oc/ref` and `#oc/deref`) to resolve vars at config-read time. Your entire system is defined declaratively under an `:oc/system` key in your Aero config.

Each component is a map using qualified keywords in the `:oc` namespace:

- `:oc/create` — **Required.** A component constructor, resolved via `#oc/ref` or `#oc/deref` (see below). If `:oc/init` is present, the constructor is called with the init map as its single argument. If `:oc/init` is absent, the constructor is called with no arguments.
- `:oc/init` — **Optional.** A configuration map passed to the constructor. Can use any Aero feature (`#ref`, `#env`, `#or`, `#profile`, etc.).
- `:oc/using` — **Optional.** A dependency list, exactly as Component expects: a vector (`[:db :cache]`) or a map (`{:database :db}`). If missing, the component has no dependencies.

Because of how Component's Lifecycle protocol works, you can also add stateless components to your system — plain functions or static values.

A quick example:

```clojure
;; config.edn
{:oc/system {:web-server #:oc {:create #oc/ref :app.component.http-server/create
                               :init {:port #long #or [#env PORT 8080]
                                      :handler #oc/deref :app.http/api-handler}
                               :using [:db :redis]}
             :db #:oc {:create #oc/ref :app.component.db/create
                       :init {:uri #env "DATABASE_URL"}}
             :redis #:oc {:create #oc/ref :app.component.redis/create
                          :init {:uri #env "REDIS_URL"}}}}
```

## Reader tags: `#oc/ref` and `#oc/deref`

Oreo provides two Aero reader tags for resolving vars in your configuration. These are **not** the same as Aero's built-in `#ref` (which navigates within the config map) — they resolve Clojure vars from your codebase at config-read time.

Both tags accept either a **keyword** or a **symbol** — internally, both are converted via `(symbol value)` and passed to `requiring-resolve`, so `#oc/ref :my.ns/create` and `#oc/ref my.ns/create` are equivalent. Keywords are slightly more idiomatic in EDN.

### `#oc/ref` — resolve a var

Returns the var itself (without dereferencing it). Use this for:

- **Component constructors** in `:oc/create` — the var is then called as a function:

  ```clojure
  :api #:oc {:create #oc/ref :app.component.http/create
             :init {:port 8080}}
  ```

- **Stateless function components** — the var can be called directly by other components:

  ```clojure
  :tracer #oc/ref :app.system/tracer
  ```

### `#oc/deref` — resolve and dereference a var

Returns the **value** the var points to, not the var itself. Use this when you need the actual runtime value:

- **Atoms or other reference types** as top-level components:

  ```clojure
  :store #oc/deref :app.system/store
  ```

- **Handler functions** inside `:oc/init` — when the component expects a function value, not a var:

  ```clojure
  :init {:handler #oc/deref :app.api/handler}
  ```

### When does the difference matter?

In most cases, calling a var and calling the function it holds behave the same — Clojure vars implement `IFn` and delegate to their value. The distinction matters when:

- You need the **actual object** (an atom, a map, a connection) rather than a callable reference — use `#oc/deref`
- You want **var indirection** for REPL reloading (redefining the function updates the var in place) — use `#oc/ref`

## Validations

Oreo uses Clojure spec to validate your system definition before creating the system map. It checks that:

- Every `:oc/create` value is a function or var
- Every `:oc/using` entry is a vector of keywords or a map
- All dependencies listed in `:oc/using` actually exist as keys in the system

## Annotated example

Here is a full example. A working version lives in the `example/` directory:

```clojure
{;; your shared configuration
 :app {:name "foobar"}
 :api {:server {:port 1002}}

 ;; your system definition, it can be here or in a different file
 ;; merged by using #include reader macro, use #profile etc etc
 :oc/system {;; An atom as a top-level component — #oc/deref gets the atom value
             :store #oc/deref :foobar.system/store

             ;; Component with config pulled from elsewhere in the EDN via Aero's #ref
             :scheduler #:oc {:create #oc/ref :utility-belt.component.scheduler/create-pool
                              :init #ref [:app]}

             ;; Depends on :scheduler and :store, uses a keyword for :create (equivalent to a symbol)
             :counter #:oc {:create #oc/ref :utility-belt.component.scheduler/create-task
                            :init {:name "counter"
                                   :period-ms 1000
                                   ;; #oc/deref because the component expects a function value
                                   :handler #oc/deref :foobar.scheduler/task-counter}
                            :using [:scheduler :store]}

             ;; A stateless component — just a function var, no lifecycle needed
             :tracer #oc/ref :foobar.system/tracer

             ;; Jetty server component with a Ring handler
             :api #:oc {:create #oc/ref :utility-belt.component.jetty/create
                        :init {:config #ref [:api :server]
                               :handler #oc/deref :foobar.api/handler}
                        :using [:store :tracer]}}}


;; in your app/system.clj:

(ns app.system
  (:require
   [aero.core :as aero]
   [oreo.core :as oreo]
   [com.stuartsierra.component :as component]
   [clojure.java.io :as io]))

;; load configuration as usual
(def config
  (aero/read-config (io/resource "config.edn")))

;; fn to start the system
(defn start []
  (-> config
      oreo/create-system
      component/start))
```

# Rationale

Why does this even exist? After working for nearly 10 years with Component, I ran into two recurring issues:

- System definitions end up being somewhat dynamic, so it's hard to see the final shape of a system. A typical scenario is conditionally enabling sets of components depending on runtime configuration.
- A lot of configuration managed by Aero ends up being just initialization values for Components anyway — so why not combine the two and remove the boilerplate?

Oreo is the answer: a thin layer (~370 lines total) that connects two proven libraries, adding only what's needed to bridge them.

## How does Oreo compare to other approaches?

There are several libraries in the Clojure ecosystem that solve the "application state and lifecycle" problem. They're all good libraries with thoughtful designs. Oreo exists for people who are already invested in Component and Aero, or who want a minimal solution that doesn't reinvent what those libraries already do well.

### vs [Integrant](https://github.com/weavejester/integrant)

Integrant is the closest in spirit — it's also data-driven, defining your system as an EDN map. The key differences:

- **Lifecycle dispatch**: Integrant uses multimethods (`init-key`, `halt-key!`) dispatched on keywords. Component uses protocols on records. Protocols are easier to navigate in an IDE ("go to definition" works), and the lifecycle implementation lives alongside the component's state rather than in a separate multimethod somewhere.
- **Configuration**: Integrant has its own `#ig/ref`, `#ig/refset`, and `#ig/profile` tags. Oreo uses Aero, which already provides `#ref`, `#env`, `#or`, `#profile`, `#include`, `#merge`, and more. Many Integrant users end up adding Aero on top for these features anyway.
- **Migration path**: If you already use Component, Oreo is additive — your existing records and Lifecycle implementations work unchanged. Integrant requires rewriting every component as multimethod implementations.
- **Concept count**: Integrant introduces keyword hierarchies (`derive`), composite keys, `expand-key` modules, `suspend!/resume` — each useful, but together they add up. Oreo's API is 3 functions and 2 reader tags.

### vs [Mount](https://github.com/tolitius/mount)

Mount takes a fundamentally different approach: global singleton state via `defstate`. It's convenient for small applications, but:

- There is no explicit dependency graph — ordering is implicit from namespace loading order.
- The system is not a value you can inspect, pass around, or run multiple instances of.
- Testing with alternative configurations requires `mount/start-with` overrides rather than simply passing a different config map.

Component (and by extension Oreo) gives you the system as a first-class value with explicit dependency wiring.

### vs [donut.system](https://github.com/donut-party/system)

donut.system is also data-driven and builds on ideas from both Component and Integrant. It's more ambitious in scope, introducing signals, channels, groups, and its own configuration layer. If you want a batteries-included framework with more built-in abstractions, it may be a good fit. Oreo aims for the opposite end of the spectrum: minimal glue code between two libraries you might already be using.


# Tips & tricks

### Code reloading

Using Oreo means your system definition lives in EDN, not code. This can clash with `tools.namespace` reloading since the config is read once at load time.

**Option 1: Instruct `tools.namespace` to reload your system namespace**

```clojure
(ns app.system
  (:require .... ))

(defn production []
  (oreo/create-system (config/load-config :production)))

(defn development []
  (oreo/create-system (config/load-config :development)))
```

Then in your REPL namespace:

```clojure
(require '[clojure.tools.namespace.repl :as tn.repl]
         '[com.stuartsierra.component :as component]
         'app.system)

(defn start []
   (let [system (app.system/development)]
     (component/start (component/map->SystemMap system))))

(def sys nil)

(defn go []
  ;; instruct t.n.repl tracker to always reload system namespace
  (alter-meta! (find-ns 'app.system) merge {::tn.repl/load true ::tn.repl/unload true})
  (tn.repl/refresh)
  (alter-var-root #'sys (fn [sys] (when-not sys (start))))
  :ready)

(defn stop []
  (alter-var-root #'sys (fn [sys]
                          (when sys
                            (component/stop sys)
                            nil)))
  :stopped)
```

**Option 2: Define the system in code instead of EDN**

You can bypass Aero and pass a system map directly to `oreo.core/make-system-map`:

```clojure
(ns app.system
  (:require [oreo.core :as oreo]
            [com.stuartsierra.component :as component]
            [app.component.http :as http]
            [app.component.postgres :as postgres]))

(defn system []
  (-> {:db #:oc {:create postgres/create
                 :init {:uri "localhost"
                        :port 5432}}
       :api #:oc {:create http/create-server
                  :init {:port 1000}
                  :using [:db]}}
      oreo/make-system-map))
```

This is less desirable as it gives up most benefits of Aero, but it works.

### I don't want to use records and protocols

Your `:oc/create` function can return anything that implements Component's `Lifecycle` protocol. Besides records, you can use the `extend-via-metadata` approach on plain maps. My [utility-belt](https://github.com/lukaszkorecki/utility-belt/blob/a3275f183a142a0a30bfe42ffccc15bf15e8c863/src/utility_belt/component.clj#L56) library provides a helper for this.

### I don't want to define my config and system map in the same file

Use Aero's `#include` reader tag to split your config across files.

### How do I create different variants of my system?

You have all of Aero's features at your disposal. Use `#profile` and `#merge` to compose system variants:

```clojure
{:db-conn {:uri "localhost"
           :port #long #or [#env "DB_PORT" 543]}

 :components {:db #:oc {:create #oc/ref :app.component.db/create
                        :init #ref [:db-conn]}
              :api #:oc {:create #oc/ref :app.component.api/create
                         :init {:port 1000}
                         :using [:db]}
              :worker #:oc {:create #oc/ref :app.component.worker/create
                            :init {:count 3}
                            :using [:db]}}

 :oc/system #profile {:default #ref [:components]
                      :api #merge [{:api #ref [:components :api]}
                                   {:db #ref [:components :db]}]

                      :worker #merge [{:worker #ref [:components :worker]}
                                      {:db #ref [:components :db]}]}}
```

Load with `(aero/read-config "config.edn" {:profile :api})` and only the API and DB components will be included.

# Status

Oreo is used in production across several backend services. The API is small and stable.

- [x] Make it work in a synthetic example
- [x] Use in something real
- [x] Clojars release
- [ ] See if any of `utility-belt.component` utils can be merged in and/or used
- [ ] Solve the reloading issue — hook into `tools.namespace`?
- [ ] Validate in larger multi-system applications
