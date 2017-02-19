(ns mailstats2.rdf
  (:require [clj-http.client :as http]
            [clj-turtle.core :as turtle]))

(turtle/defns mail "http://tenforce.example.com/vocab/mail/")

(turtle/defns tenforce "http://tenforce.example.com/")

(turtle/defns foaf "http://xmlns.com/foaf/0.1/")

(defn user-ent [id]
  (tenforce (str "user/" id)))

(defn make-triples [& triples]
  "Wraps turtle/rdf to allow expressions evaluating to nil to be concat-ed out."
  "(make-triples [a b c] nil [d e f]) => (turtle/rdf a b c d e f)."
  (apply turtle/rdf (apply concat triples)))

(defn user-triples [p]
  (let [user-entity (user-ent (:id p))]
    (make-triples
      [user-entity (turtle/a) (foaf :Person)]
     (when (:name p)
       [user-entity (foaf :Name) (turtle/literal (:name p))])
     [user-entity (foaf :mbox) (turtle/literal (:email p))])))

(defn email-triples [get-uid m]
  (let [mail-entity (tenforce (str "email/" (:id m)))]
    (make-triples
     [mail-entity (turtle/a) (mail :Mail)]
     [mail-entity (mail :title) (turtle/literal (:title m))]
            
     (mapcat #(list mail-entity (mail :from) (user-ent (get-uid (:email %))))
             (:from m))

     (mapcat #(list mail-entity (mail :to)  (user-ent (get-uid (:email %))))
             (:to m)))))

(defn sparql-insert
  ([triples] (sparql-insert triples nil ""))
  ([triples graph] (sparql-insert triples graph nil))
  ([triples graph cond]
   (str "INSERT DATA \n{"
        (when graph (str "GRAPH " graph " { " ))
        triples
        (when graph " } ")
        "\n}"
        (when cond (str " WHERE { " cond " }")))))

(defn sparql-post [endpoint username password query]
  (try
    (http/post endpoint
               { :basic-auth [username password]
                :body query
                :content-type "application/sparql-update"
                :accept "text/boolean"})
    (catch Exception e e)))

(defn sparql-get [endpoint query]
  (http/get endpoint
             { :body query
              :content-type "application/sparql-update"
              :accept "text/boolean"}))

(defn put-all-triples [config stats get-uid]
  (pmap #(sparql-post
          (:sparql-endpoint config) (:sparql-user config) (:sparql-pass config)
          (sparql-insert % (tenforce "nathaniel")))
        
        (concat
         (map user-triples (vals (:Users stats)))
         (map (partial email-triples get-uid) 
              (vals (:Messages stats))))))
