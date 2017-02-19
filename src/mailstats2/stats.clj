(ns mailstats2.stats
  (:require [clojure.core.reducers :as r]))

(defn rules [get-uid]
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
    :indexes #(re-seq #"\[[^]]+\]" (or (:title %) ""))
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
    :reducer (fn [a b]
               [(+ (first a) (first b))
                     (+ (second a) (second b))])
    }

   {
    :name :Users
    :indexes #(concat (:from %) (:to %))
    :indexer :email
    :value #(assoc % :id (get-uid (:email %)))
    :reducer (fn [a b] a)
    }

   {
    :name :Messages
    :indexes list
    :indexer :id
    :value identity
    :reducer merge
    }
   ])

(defn run [messages rules]
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
