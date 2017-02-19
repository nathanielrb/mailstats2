(ns mailstats2.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]])
  (:use [hiccup core page]
        [mailstats2.gmail :as gmail]
        [mailstats2.rdf :as rdf]
        [mailstats2.stats :as stats]
        [mailstats2.views :as views]
        ring.util.response))

(def config
  {
   :client-id "343994728240-jbjvn00flugpt1agu5sl78q0e66cj5qn.apps.googleusercontent.com"
   :client-secret "jbDDu-MbePeQ8z4vBgz5_uH1"
   :client-auth-url "http://localhost:3000/auth"
   :sparql-endpoint "http://5.9.241.51:8890/sparql"
   :sparql-user "dba"
   :sparql-pass "dba"
   })

(def uid-counter (atom 0))

(def uids (atom {}))

(defn get-uid [email]
  (or (get @uids email)
      (let [id (swap! uid-counter inc)]
       (swap! uids (fn [rec]
                     (assoc rec email id)))
       id)))

(defn theapp [config token]
  (let [s
        (stats/run
          (take 10 (gmail/messages token))
          (stats/rules get-uid))
        r (rdf/put-all-triples config s get-uid)]
    
    (views/stats-page s r)))

(defroutes app-routes
  (GET "/" [] (views/login-page))
  (GET "/auth" [code] (views/auth-page config code))
  (GET "/app" [token] (theapp config token))

  (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes site-defaults))
