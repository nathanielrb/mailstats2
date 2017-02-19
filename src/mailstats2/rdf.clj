(ns mailstats2.rdf
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clj-http.client :as http]
            [clj-turtle.core :as turtle])
;            [clj-sparql.core :as sparql]))
  (:use [hiccup core page]
        ring.util.response))

(defn sparql-insert [triples cond]
  (str "INSERT { " triples " } WHERE { " cond " }"))

(defn sparql-post [endpoint username password query]
  (http/post endpoint
             { :basic-auth [username password]
              :body query
              :content-type "application/sparql-update"
              :accept "text/boolean"}))

(defn make-triples [& triples]
  "Wraps turtle/rdf to allow expressions evaluating to nil to be concat-ed out."
  "(make-triples [a b c] nil [d e f]) => (turtle/rdf a b c d e f)."
  (apply turtle/rdf (apply concat triples)))
