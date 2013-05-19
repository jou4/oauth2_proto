(defproject auth "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [compojure "1.1.5"]
                 [org.clojure/data.json "0.2.2"]]
  :plugins [[lein-ring "0.8.3"]]
  :ring {:handler auth.handler/app :port 3000}
  :profiles
  {:dev {:dependencies [[ring-mock "0.1.3"]]}})
