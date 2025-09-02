# Oreo

> [!WARNING]
> This is not even pre-alpha quality software; expect breakage!

# What is this?

Oreo combines [Component](https://github.com/stuartsierra/component) and [Aero](https://github.com/juxt/aero) giving you
the ability to declaratively define your system, along with the configuration of your components, in a single `config.edn` file.

> [!NOTE]
> I'm using Title-Case for the Component library and lower-case for a component to mean the **actual** components in your system.

# Installation

TBC

## Annotated Example

Here is an example of how you might define your system in a `config.edn` file. You can find a dummy application in the `example` which uses this config:

```clojure
{;; your shared configuration
 :app {:name "foobar"}
 :api {:server {:port 1002}}

 ;; your system definition, it can be here or in a different file
 ;; merged by using #include reader macro, use #profile etc etc
 :oc/system {;; We're using `ref!` here to get the actual atom from the var
             :store #oc/deref foobar.system/store

             ;; See utility-belt.component.scheduler for more details
             ;; creates a scheduled threadpool exector with given name
             ;; shows how parts of config map can be referenced using Aero's `#ref` syntax
             :scheduler #:oc {:create #oc/deref utility-belt.component.scheduler/create-pool
                              :init #ref [:app]}
             ;; follows from above - let's add a task to the scheduler with required config
             ;; for 'fun' we're using a keyword rather than a symbol, which is a bit more idiomatic
             ;; and we are using #ref to return a var rather than a function, in case of `:create` key - either will work
             :counter #:oc {:create #oc/ref :utility-belt.component.scheduler/create-task
                            :init {:name "counter"
                                   :period-ms 1000
                                   ;; again - using #ref! because a function (not a var) is expected
                                   :handler #oc/deref foobar.scheduler/task-counter}
                            ;; dependency injection demo - the task will be able to access the scheduler component as well as the store
                            :using [:scheduler :store]}

             ;; demo of stateless component, which is just a function, and doesn't need to be `#ref!`ed
             :tracer #oc/ref :foobar.system/tracer

             ;; This is a Jetty server component, which uses a handler function from the API namespace
             :api #:oc {:create :utility-belt.component.jetty/create
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
      ;; use ^^^ to expand into system map
      oreo/create-system
      ;; and start it
      component/start))
```

# How does this work?

It's **very simple** ;-) Oreo plugs into Aero's facilities and defines a couple of extra reader tags to simplify looking up vars, which are used to create component instances, references to handlers, etc.

In your config map (the one you load using `aero.core/read-config`), add an `:oc/system` key with a map that defines your system. Name your component keys as you would normally, and define them as maps with special keys. They are:

- `:oc/create`  - A fully qualified, namespaced keyword or symbol referencing a function that creates your component. It will receive a single argument - the config map read from `:oc/init`.
- `:oc/init` - **Optional**, A configuration map for your component, either defined inline or using the `#ref` reader macro or any other facility provided by Aero (`#env`, `#or`, etc.) it's used as the value passed to the component constructor (`map->MyComponent` etc)
- `:oc/using` - **Optional** dependency list for the component. It can be a vector or a map, just like Component expects it to be. If missing, the component will be constructed without any dependencies.

Because of Component's Lifecycle protocol works, you can also use Oreo to add stateless components to your system such as functions or static values.

## When to use which tag?

Oreo provides two reader tags, `#oc/ref` and `#oc/deref`, to simplify referencing functions and values in your configuration. Here’s how to choose between them:

-   `#oc/ref` should be used when you need to reference a **var** itself, not the value it contains. A good example of this is a "stateless" component that is just a function.

    ```clojure
    :tracer #oc/ref :foobar.system/tracer
    ```

-   `#oc/deref` should be used when you need the **value** of a var. This is useful when a component expects a function or a value directly, not a var. For example, if you have an atom defined in a namespace and you want to pass it to a component as a dependency, you would use `#oc/deref` to get the atom itself.

    ```clojure
    :store #oc/deref foobar.system/store
    ```

    Similarly, if a component expects a handler function, you would use `#oc/deref` to pass the function itself, not the var that holds it.

    ```clojure
    :handler #oc/deref :foobar.api/handler
    ```

## Validations

Oreo will use Clojure spec to ensure right configuration is passed, as well checks to ensure that dependencies specified in `:using` are also present in the system.


# Caveats, Gotchas, Notes, Q&A and Tips & Tricks

### Rationalle

Why does this even exist? After working for nearly 10 years with Component, I run into two main issues:

- system definitions end up being somewhat dynamic so it's hard to see the final shape of a system, typical scenario is conditionally enabling sets of components depending on run time configuration
- a lot of configuration defined in a config map managed by Areo ends up being just initialization values for Components anyway so why not combine the two into one thing and remove some boilerplate


### So it's like [Integrant](https://github.com/weavejester/integrant)?

Maybe. I have never used it in anger, but it looks vaguely similar. The reason why Oreo exists is because:

- Component already solved this problem, and because of its reliance on protocols and records, it can be integrated (heh) with the Java ecosystem in a more flexible way than Integrant.
- Oreo doesn't reinvent what Aero does already - Integrant has its own notion of `ref`, etc.
- Most importantly, Oreo is meant to help existing Component users rather than require rewriting code.

### Code reloading, renaming, etc.

Obviously, using Oreo clashes with reloading your code, renaming things, etc., since everything is declarative.
The "typical" usage of Component is not susceptible to this because your system is defined as part of regular code. Just be careful about reloading things.

A way around it would be to bypass Aero layer, and use Oreo directly by passing a system map to `oreo.core/create-system`:

```clojure
(ns app.system
  (:require [oreo.core]
            [app.component.http :as http]
            [app.component.postgres :as postgres]))


(defn system []
  (oreo.core/create-system {:db #:oc {:create postgres/create
                                      :init {:uri "localhost"
                                             :port 5432}}
                            :api #:oc {:create http/create-server
                                       :init {:port 1000}
                                       :using [:db]}}))
```

> [!NOTE]
> I'm thinking of ways of addressing this.


### I don't want to use records and protocols, they smell like Java

That's not really my or Oreo's problem, but here's what you can do:

- Use records.
- Your `:create` function can return something that implements the `Lifecycle` protocol using the `extend-via-metadata` approach. My `utility-belt` library [provides a small function to create components out of maps](https://github.com/lukaszkorecki/utility-belt/blob/a3275f183a142a0a30bfe42ffccc15bf15e8c863/src/utility_belt/component.clj#L56)


### I don't want to define my config and system map in the same file

Use `#include` - see Aero's docs for more info, or see the tip above.


### How do I create different variants of my system?

Remember, you have all features of Aero at your disposal, including `#profile` reader tag - see tests in `core_test.clj` to see how it can be used.
This should give you an idea how to put it together:

```clojure
{:db-conn {:uri "localhost"
           :port #long #or [#env "DB_PORT" 543]}

 :components {:db #:oc {:create #oc/ref oreo.core-test/create-dummy
                        :init #ref [:db-conn]}
              :api #:oc {:create #oc/ref oreo.core-test/create-dummy
                         :init {:port 1000}
                         :using [:db]}
              :worker #:oc {:create #oc/ref oreo.core-test/create-dummy
                            :init {:count 3}
                            :using [:db]}}

 :oc/system #profile {:default #ref [:components]
                      :api #merge [{:api #ref [:components :api]}
                                   {:db #ref [:components :db]}]

                      :worker #merge [{:worker #ref [:components :worker]}
                                      {:db #ref [:components :db]}]}}

```

Now when reading config via `aero/read-config` with different profile value, your system will be composed
according to the profile name. Again: you have all of Aero's tools available at your disposal.

# Status/Roadmap/TODO

> [!WARNING]
> Alpha quality warning has to be repeated here
> I'm using Oreo in a couple of applications doing work every day, but it has not been validated in bigger systems


- [x] Make it work in a synthetic example.
- [x] Use in something real.
- [ ] Finalize naming & API.
- [ ] See if any of `utility-belt.component` utils can be merged in and/or used.
- [ ] Somehow solve the reloading issue - hook into `tools.namespace`?
- [ ] Use in something real and complicated.
- [ ] Clojars release.
