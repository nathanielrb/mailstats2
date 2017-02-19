(ns mailstats2.views
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]])
  (:use [hiccup core page]
        [mailstats2.gmail :as gmail]
        [mailstats2.rdf :as rdf]
        [mailstats2.stats :as stats]
        ring.util.response))

(defn pl-s [s]
  (when (>  s 1) "s"))

(defn pl-es [s]
  (when (> s 1) "es"))

(defn stats-box [title description content]
  [:div {:class "stat"}
   [:h2 title
    (when description [:small description])]
   content])

(defn calc-mbs [stats]
  (float (apply / (second (first (:MeanBodySize stats))))))

(defmacro page [title header body]
  `(try
     (html5
      [:head
       [:title ~title]
       (include-css "/css/style.css")]
      [:body
       [:h1 ~header]
       ~body
       ])
    (catch Exception e#
      (html5
       [:h1 "Error"]
       [:p (.getMessage e#)]))))

(defn display-stats [messages statistics users rdf-responses]
  [:div {:class "stats"}

   (stats-box
    "Top Five Senders" "In 'From' and 'Sender' fields"
    (map #(let [sender (first %)]
            (list [:p {:class "email"
                       :title (:name (get users sender))}
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
    (map #(list [:p (first %) " address" (pl-es (first %))
                 [:b (second %) " email" (pl-s (second %))]])
         (sort-by second > (:NumberOfAddresses statistics))))
   
   (stats-box
    "RDF Triples" nil
    (str "Updated " (count rdf-responses) " triples"))
   
   ])

(defn stats-page [statistics rdf-responses]
  (let [messages (:Messages statistics)
        users (:Users statistics)]
    (page "Stats" "Some Statistics on your Mailbox"
          (display-stats messages statistics users rdf-responses))))

(defn login-page []
  (page "Email stats for Tenforce" "Email stats for Tenforce"
        [:p
         [:a {:href "/auth"}
          "Authorize one time on Gmail"]]))

(defn auth-page [config code]
  (if code
    (let [token (gmail/oauth-get-token code config)]
    (redirect (str "/app?token=" token)))
    (redirect (auth-url config))))

;; for repl testing
(defn token-page [code config]
  (if code
    (gmail/oauth-get-token code config)
    (redirect (auth-url (assoc config :client-auth-url "http://localhost:3000/token")))))
