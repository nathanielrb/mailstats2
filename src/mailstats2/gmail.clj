(ns mailstats2.gmail
  (:require [clj-http.client :as http]))

(defn auth-url [config]
  (str "https://accounts.google.com/o/oauth2/v2/auth?"
       "scope=https://www.googleapis.com/auth/gmail.readonly https://www.googleapis.com/auth/userinfo.email"
       "&state=onetimeonly"
       "&redirect_uri=" (:client-auth-url config)
       "&response_type=code"
       "&client_id=" (:client-id config)))

(defmacro google-api [body]
  `(try
     ~body
     (catch Exception e#
       (throw (Exception.
               (do (println e#)
               (str "Error accessing Google API: "
                     (.data e#))))))))

(defn oauth-get-token [code config]
  (google-api
    (:access_token
     (:body 
      (http/post "https://www.googleapis.com/oauth2/v4/token" ;; to config
                 {:form-params {:code code
                                :client_id (:client-id config)
                                :client_secret (:client-secret config)
                                :redirect_uri (:client-auth-url config)
                                :grant_type "authorization_code" }
                  :content-type "application/x-www-form-urlencoded"
                  :as :json})))))

(defn gmail-get
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

(defn filter-field [field-name m]
  "field-name can be a string or a set of strings.
   (filter-field #{\"From\" \"To\" msg) => ({ :name \"From\" :value \"bob@gmail.com\"} ...)"
  (let [fields (if (string? field-name) #{field-name} field-name)]
    (filter #(fields (:name %))
            (:headers (:payload m)))))

(defn split-email [full-email]
  (let [match
        (re-find
         (re-matcher
          #"(?:\s?\"?([^<\",]+[^\s\"])\"?\s*<(.+)>)|(^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[A-Za-z]{2,}$)"
          (or full-email "")))]
    {:email (or (nth match 3) (nth match 2))
     :name (nth match 1)}))

(defn extract-fields [msg]
  {:id (:id msg)
   :sizeEstimate (:sizeEstimate msg)
   :from (map
          split-email
          (mapcat
           (partial re-seq #"[^,]+")
           (map #(or (:value %) "") (filter-field #{"From" "Sender"} msg))))
   :to  (map
         split-email
         (mapcat
          (partial re-seq #"[^,]+")
          (map #(or (:value %) "") (filter-field "To" msg))))
   :title (first
           (map
            :value
            (filter-field "Subject" msg)))})

(defn messages [token]
  (let [user (gmail-get-user token)]
    (println user)
    (pmap #(extract-fields (gmail-get-message token user %))
           (map :id (gmail-get-messages token user)))))
