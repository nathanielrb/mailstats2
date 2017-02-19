(defproject mailstats2 "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [compojure "1.5.1"]
                 [ring/ring-defaults "0.2.1"]
                 [hiccup "1.0.5"]
                 [clj-http "2.3.0"]
                 [cheshire "5.7.0"]
                 [clj-turtle "0.1.3"]
                 [clj-sparql "0.2.0"]
                 ]
  :plugins [[lein-ring "0.9.7"]]
  :ring {:handler mailstats2.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring/ring-mock "0.3.0"]]}})
