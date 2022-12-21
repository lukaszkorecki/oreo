(ns foobar.widget)


(defprotocol Widgetized

  (report [this]))


(defrecord Widget
  [profile]

  Widgetized

  (report
    [this]
    (format "widget with profile: %s" profile)))


(defn make
  [profile]
  (->Widget profile))
