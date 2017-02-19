(ns mailstats2.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [clj-http.client :as http]
            ;[org.httpkit.client :as http]
            [clj-turtle.core :as turtle]
            [clojure.core.reducers :as r])
  (:use [hiccup core page]
        [mailstats2.google :as google]
        [mailstats2.rdf :as rdf]
        ring.util.response))

;;; TODO
;; - forwarding - remove 'code' from path, to allow reload

;; Config

(def config
  {
   :client-id "343994728240-jbjvn00flugpt1agu5sl78q0e66cj5qn.apps.googleusercontent.com"
   :client-secret "jbDDu-MbePeQ8z4vBgz5_uH1"
   :client-url "http://localhost:3000/app"
   :sparql-endpoint "http://localhost:5820/mydb/update"
   :sparql-user "admin"
   :sparql-pass "admin"
   })

(def temp-token "ya29.Glz3AzlRy3H-qUvDP_Tn1dVzqqf3DqvgOcZcUsK0HyaW5p-YoOkSkLoY2gXBmOboUZcIsVktkGC5VoDcjkfZJk7A9_eIVq5R2MzzQUtNCeMWs9thbchVtlaw3_uAQw")

(def uid-counter (atom 0))

(def uids (atom {}))

(defn filter-field [field-name m]
  "'field-name' may be a string or a set of strings."
  (let [fields (if (string? field-name) #{field-name} field-name)]
    (filter #(fields (:name %))
            (:headers (:payload m)))))

(defn split-email [full-email]
  (let [match
        (re-find
         (re-matcher
          #"(?:\s?\"?([^<\",]+[^\s\"])\"?\s*<(.+)>)|(^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[A-Za-z]{2,}$)"
          full-email))]
    {:email (or (nth match 3) (nth match 2))
     :name (nth match 1)}))

(defn extract-fields [msg]
  {:id (:id msg)
   :sizeEstimate (:sizeEstimate msg)
   :from (map
          split-email
          (mapcat
           (partial re-seq #"[^,]+")
           (map :value (filter-field #{"From" "Sender"} msg))))
   :to  (map
         split-email
         (mapcat
          (partial re-seq #"[^,]+")
          (map :value (filter-field "To" msg))))
   :title (first
           (map
            :value
            (filter-field "Subject" msg)))})

(defn messages [user token]
    (pmap #(extract-fields (google/gmail-get-message token user %))
           (map :id (google/gmail-get-messages token user))))

(defn test-msgs [n]
  (take n (messages (google/gmail-get-user temp-token) temp-token)))

(defn get-uid [email]
  (or (get @uids email)
      (let [id (swap! uid-counter inc)]
       (swap! uids (fn [rec]
                     (assoc rec email id)))
       id)))

(defn run-stats [messages rules]
  (r/fold
   merge
   (r/map
    (fn [rule]
      {(:name rule)
       (r/fold
        (partial merge-with (:reducer rule))
        (r/map
         (fn [msg]
           (apply
            merge
            (map
             #((if (:sorted? rule) sorted-map hash-map)
               ((or (:indexer rule) identity) %)
               ((:value rule) %))
             ((:indexes rule) msg))))
         messages))
       })
    rules)))

;; RDF Triples

(turtle/defns mail "http://tenforce.example.com/vocab/mail/")
(turtle/defns tenforce "http://tenforce.example.com/")
(turtle/defns foaf "http://xmlns.com/foaf/0.1/")

(defn user-ent [id]
  (tenforce (str "user/" id)))

(defn user-triples [p]
  (let [user-entity (user-ent (:id p))]
    (rdf/make-triples
      [user-entity (turtle/a) (foaf :Person)]
     (when (:name p)
       [user-entity (foaf :Name) (turtle/literal (:name p))])
     [user-entity (foaf :mbox) (turtle/literal (:email p))])))

(defn email-triples [get-uid m]
  (let [mail-entity (tenforce (str "email/" (:id m)))]
    (rdf/make-triples
     [mail-entity (turtle/a) (mail :Mail)]
     [mail-entity (mail :title) (turtle/literal (:title m))]
            
     (mapcat #(list mail-entity (mail :from) (user-ent (get-uid (:email %))))
             (:from m))

     (mapcat #(list mail-entity (mail :to)  (user-ent (get-uid (:email %))))
             (:to m)))))

(defn all-triples [emails users]
  (concat
   (map user-triples users)
   (map (partial email-triples get-uid) emails)))
   
(defn put-triples [config emails users]
  (pmap #(rdf/sparql-post
          (:sparql-endpoint config) (:sparql-user config) (:sparql-pass config)
          (sparql-insert % ""))
   (all-triples emails users)))

;; Rules

(def stats-rules
  [{
    :name :Senders
    :sorted? true
    :indexes :from
    :indexer :email
    :value (constantly 1)
    :reducer +
    }

   {
    :name :Tags
    :indexes #(re-seq #"\[[^]]+\]" (:title %))
    :value (constantly 1)
    :reducer +
    }

   {
    :name :NumberOfAddresses
    :indexes #(list (count (concat (:from %) (:to %))))
    :value (constantly 1)
    :reducer +
    }

   {
    :name :MeanBodySize
    :indexes #(list (:sizeEstimate %))
    :indexer (constantly '(totalsize))
    :value (fn [size] [size 1])
    :reducer (fn [a b] (list (+ (first a) (first b)) (+ (second a) (second b))))
    }

   {
    :name :Users
    :indexes #(concat (:from %) (:to %))
    :indexer :email
    :value #(assoc % :id (get-uid (:email %)))
    :reducer (fn [a b] a)
    }
   ])

;; Web

(defn pl-s [s]
  (when (>  s 1) "s"))

(defn pl-es [s]
  (when (> s 1) "es"))

(defn stats-box [title description content]
  [:div {:class "stat"}
   [:h2 title
    (when description [:small description])
    ]
   content])

(defn calc-mbs [stats]
  (float (apply / (second (first (:MeanBodySize stats))))))

(defn display-stats [config token rules]
  (let [user (google/gmail-get-user token)
        messages (take 10000 (messages user token))
        statistics (run-stats messages rules)
        users (:Users statistics)
        rdf-responses (put-triples config messages (vals users))]

  (html5
     [:head
      [:title "Some stats"]
      (include-css "/css/style.css")]
     [:body
      [:p "Token: " token]
      [:h1 (str "Stats: " user)]
      [:div {:class "stats"}

       (stats-box
        "Top Five Senders" "In 'From' and 'Sender' fields"
        (map #(let [sender (first %)]
                (list [:p {:class "email"
                           :title (str
                                   (:name (get users sender))
                                   " ("(get-uid sender) ")")
                           }
                       (h sender)
                       [:b (second %) " email" (pl-s (second %))]]))
             (take 5 (sort-by second > (:Senders statistics)))))

       (stats-box
        "Average Body Size" nil
        (str (calc-mbs statistics) " bytes"))
       
       (stats-box
        "Top Five Tags" nil
        (map #(list [:p (first %)
                     [:b (second %) " email" (pl-s (second %))]])
             (take 5 (filter #(first %) (sort-by second > (:Tags statistics))))))

       (stats-box
        "Addresses Per Email" "In 'To', 'From', and 'Sender' fields"
        (map #(list [:p (first %) " address"
                     (pl-es (first %))
                     [:b (second %) " email" (pl-s (second %))]])
             (:NumberOfAddresses statistics)))

       (stats-box "RDF Triples"
                  nil
                 (str "Updated " (count rdf-responses) " triples"))

       ]])))




(defmacro page [body]
  `(try
    ~body
    (catch Exception e#
      (html5
       [:h1 "Error"]
       [:p (.getMessage e#)]))))

(defn auth-page []
    (html5
     [:head
      [:title "email stats for Tenforce"]
      (include-css "/css/app.css")]
     [:body
      [:h1 (str "email stats for Tenforce")]
      [:p
       [:a {:href "/app"} "Authorize one time Gmail"]]]))

(defn app-page [code]
  (page
   (if code
     (let [token (google/oauth-get-token code config)]
;       token)
       (display-stats config token stats-rules))
     (redirect (auth-url config)))))

(defn token-page [code]
  (page
   (if code
     (google/oauth-get-token code config)
     (redirect (auth-url config)))))

(defroutes app-routes
  (GET "/" [] (auth-page))
  (GET "/app" [code] (app-page code))
  (GET "/token" [code] (token-page code))
  (route/not-found "Not Found"))

(def app
  (wrap-defaults app-routes site-defaults))
