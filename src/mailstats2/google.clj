(ns mailstats2.google
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clj-http.client :as http]
            [clj-turtle.core :as turtle])
;            [clj-sparql.core :as sparql]))
  (:use [hiccup core page]
        ring.util.response))

;; Gmail Oauth2

(defn auth-url [config]
  (str "https://accounts.google.com/o/oauth2/v2/auth?"
       "scope=https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/userinfo.email"
       "&state=onetimeonly"
       "&redirect_uri=" (:client-url config)
       "&response_type=code"
       "&client_id=" (:client-id config)))

(defmacro google-api [body]
  `(try
     ~body
     (catch Exception e#
       (throw (Exception.
               (do (println e#)
               (str "Error getting Google Oauth token: "
                     (.data e#))))))))

(defn oauth-get [token service v type params config]
  (google-api
   (:body 
    (http/post (str "https://www.googleapis.com/" service "/v" v "/" type) ;; to config
               {:form-params (merge params
                                    {:client_id (:client-id config)
                                     :client_secret (:client-secret config)
                                     :redirect_uri (:client-url config)
                                     :grant_type "authorization_code" })
                :content-type "application/x-www-form-urlencoded"
                :as :json}))))

(defn oauth-get-token [code config]
  (google-api
    (:access_token
     (:body 
      (http/post "https://www.googleapis.com/oauth2/v4/token" ;; to config
                 {:form-params {:code code
                                :client_id (:client-id config)
                                :client_secret (:client-secret config)
                                :redirect_uri (:client-url config)
                                :grant_type "authorization_code" }
                  :content-type "application/x-www-form-urlencoded"
                  :as :json})))))

;; THREAD USER!!!

(defn gmail-get
  "Only implements GETs of 'Users.messages', does not implement media uploads."
  ([token user type method] (gmail-get token user type method nil {}))
  ([token user type method id] (gmail-get token user type method id {}))
  ([token user type method id params]
  (try
    (let [explicit-methods #{"modify" "send" "trash" "untrash"
                             "import" "batchDelete" "batchModify"}
          uri (str "https://www.googleapis.com/gmail/v1/"
                   "users/" user
                   "/" type
                   (when (explicit-methods method) (str "/" method))
                   (when id (str "/" id)))]
       (:body
        (http/get uri
                  {:query-params (merge params {:access_token token })
                   :content-type "application/x-www-form-urlencoded"
                   :as :json})))
    (catch Exception e
       (throw (Exception. (str "Error getting messages: " (.body (.data e)))))))))

(defn gmail-get-message [token user id]
  (gmail-get token user "messages" "get" id {:format "metadata"}))

(defn gmail-get-messages
  ([token user]
   (gmail-get-messages token user nil))
  ([token user pagetoken]
   (println (str "Gmail Users.messages.list, pagetoken: " pagetoken))
   (let [messages
         (gmail-get token user "messages" "list" nil {:maxResults 1000 :pageToken pagetoken})]
     (concat (:messages messages)
             (lazy-seq
              (when (:nextPageToken messages)
                (gmail-get-messages token user (:nextPageToken messages))))))))
        

(defn gmail-get-user [token]
  (:emailAddress
   (gmail-get token "me" "profile" "getProfile")))
