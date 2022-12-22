# Oreo

> **Warning** this is pre-alpha quality software, expect breakage!

# What is this?

Combines [Component](https://github.com/stuartsierra/component) and [Aero](https://github.com/juxt/aero) so that you can declaratively define your System, along with configuration of your Components in a single config.

> **Note** I'm using Title-Case for Component the library, and lower-case component to mean the **actual** components in your system

# Installation

TBC

# Annotated example


```clojure
{
 ;; your shared configuration
 :profile #or [#env PROFILE "development"]
 :api { :server {:port #long #or [#env PORT 1002]
                 :bind "0.0.0.0"}}

 ;; your system definition, it can be here or in a different file
 ;; merged by using #include reader macro
 :oreo/system {
               ;; we're using namespace keys shorthand syntax for maps
               :web-server #:oreo{;; where to find the function that creates the component
                                  :create :foobar.server/create
                                  ;; what is the configuration that :oreo/create function
                                  ;; expects
                                  :config #ref [:api :server]

                                  ;; component dependency list, optional
                                  :dependencies [:widget :uuid]}
               ;; functions can be components too!
               :uuid #:oreo {:create #oreo/fnc :clojure.core/random-uuid}

               ;; function components can have dependencies
               :printer #:oreo {:create #oreo/fnc-w-deps :clojure.core/str
                                :dependencies [:uuid]}

               ;; good old record-based component
               :widget #:oreo {:create :foobar.widget/make
                               :config #ref [:profile]
                               }


               }
 }

```

# How to use it?

It's **very simple**, here's what you need to do:

In your config map (the one you load using `aero.core/read-config`) add `:oreo/system` key with a map that defines your system. Name your component keys as you would normally, and define them as maps with special keys. They are:


- `:oreo/config` - a configuration map for your component, either defined inline or using `#ref` reader macro or any other facility provided by Aero (`#env`, `#or` etc) used to produce a configuration map
- `:oreo/create`  - a fully qualified, namespaced keyword referencing a function that creates your component, it will receive a single argument - the config map read from `:oreo/config`. See below for more information
- `:oreo/dependencies` - **optional** dependency list for the component, can be a vector or a map - just like Component expects it to. If missing, component will be constructed without them.

## Function components

Sometimes it's required to pass functions as dependencies to other components. You can use two reader macros to resolve your functions:

- `#oreo/fnc` - a FuNctional Component, no dependencies on its own, accepts 1 argument
- `#oreo/fnc-w-deps` - functional component with dependencies on of its own, will receive its dependencies map as first argument, accepts 2nd argument

You can extend `aero.core/reader` multimethod to return other things that can be used as components and/or component dependencies.

Once you define your system map, you can load your config, process it with Oreo and pass it to Component
and start it as you would normally:


```clojure
(-> "config.edn"
    aero/read-conifg
    oreo.core/create-system
    component/start)
```

# Caveats, gotchas and Q&A

### Your component has no config or lifecycle

Odd, I'd say you're *holding it wrong*, but if you really have this requirement make your `:create` function ignore any args it recevies. Alternatively you can use `oreo/fnc` to make a function into a component.


### Renaming your component's namespace or `create` function

Obviously using Oreo clashes with reloading your code, renaming things etc since everything is declarative. "Normal" usage of Component is not susceptible to this because your system is defined as part of your codebase. Just be careful about reloading things.


### I don't want to use records

That's not really my problem, but here's what you can do:

- use records
- your `:create` function can return something that implements the `Lifecycle` protocol using `extend-via-metadata` approach
- use `#oreo/fnc` to get a function-as-a-component


### I don't want to define my config and system map in the same file

Use `#include` - see here for more information: https://github.com/juxt/aero#include


### So it's like [Integrant](https://github.com/weavejester/integrant)?

Maybe, I never used it but it looks vaguely similar. Personally, I find Integrant far more complicated: derived keywords, ability to suspend components and it seems to be reimplementing a lot of what Aero already does. Again, I don't have much experience with it, but I worked on many Clojure projects that used Component and I tend to think that it solved the problem of structuring applications and dependency injection well enough that we don't really need anything else. But that's just me, you do you.

Secondly, Oreo is meant to help existing Component users: migration boils down to rewriting how your system map is built into the expected Oreo map. See the next section.

### Migrating to Oreo

This assumes you're already using Aero and Component. Or Component at least.


Given this `config.edn`:

```clojure
{
 :api-server {:port 8000}
 :redis {:host "0.0.0.0"
         :port 6379}
 :pg {:host "0.0.0.0"
      :username "foo"
      :password "password"
      :dbname "bananas"
      :dbtype "Postgres"
      }
}
```
and this `app.system` code:


```clojure
(ns app.system
  (:require [com.stuartsierra.component :as component]
            [aero.core :as aero]
            [clojure.java.io :as io]
            [next.jdbc.connection :as connection]
            [app.component.web-server :as web-server]
            [app.component.redis :as redis])
  (:import (com.zaxxer.hikari HikariDataSource)))


(defn pg-component [config]
  (connection/component HikariDataSource config))

(defn create []
  (let [config (aero/read-config (io/resource "config.edn"))
        sys {
             :db (db-component (:pg config))
             :redis (redis/create (:redis config))
             :web-server (component/using
                          (web-server/create (merge {:handler app.http/handler}
                                                    (:api-server config)))
                          [:db :redis])}]
    (component/map->SystemMap sys)))
```


Becomes (after some changes) something like this:


```clojure
{
 :api-server {:port 8000}
 :redis {:host "0.0.0.0"
         :port 6379}
 :pg {:host "0.0.0.0"
      :user "foo"
      :password "password"
      :dbname "bananas"
      :dbtype "Postgres"
      }

 :oreo/system {
               :db #:oreo {:config #ref [:pg]
                           :create :app.system/pg-component}
               :redis #:oreo {:config #ref [:redis]
                              ;; no wrappers needed!
                              :create :app.component.redis/create}
                    ;;; see below for notes, this is specific to components that accept functions
               ;; as part of their configuration, some components will be have to be rewritten
               :web/handler #:oreo {:create :app.http/handler-component  }
               :web/server #:oreo {:create :app.component.web-server/create
                                   ;; note this is standard Component syntax
                                   ;; for aliasing dependencies from the system map
                                   :dependencies {:db :db
                                                  :redis :redis
                                                  :handler :web/handler}}

               }

 }
```
Some components "constructors" can be referenced without any changes, some will require small wrapper functions. YMMV.

The second adjustment you have to make is to make function arguments to your components into proper components themselves, which in my opinion is a good practice anyway. Here's an example: https://github.com/nomnom-insights/utility-belt.http/blob/e35c1ba69281384dfa52d6de2031acd6e947e948/src/utility_belt/http/component/server.clj

### Some of my components are pretty complicated and can't be initiallized with config-as-data only

I get it, Oreo is not a silver bullet, but nothing is stopping you from doing something like this:

```clojure
(ns app.system )

(defn create []
  (let [conifg (aero/read-config (io/resource "config.edn"))
        sys-map-base (oreo/resolve-system-from-config (:oreo/system config))
        legacy-system-map {:my-gnarly-component (component/using
                                                 (app.gnarly/create {:handler async.processor/handler
                                                                     :post-processor (fn [job]
                                                                                       (update job :name str/reverse))
                                                                     :config (get-in config [:rabbitmq :consumer :gnarly])})
                                                 [:db :redis :rmq])}]
    (component/map->SystemMap (merge sys-map-base legacy-system-map))))
```

Remember, "It's just data a functions"

# Status/roadmap


- [x] make it work in a synthetic example
- [ ] finalize naming/API
- [ ] use in something real
- [ ] Clojars release
