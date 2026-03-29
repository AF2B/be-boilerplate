(ns com.borba.be-boilerplate.handlers.business.order
  "Business logic for Order domain.
  
   Demonstrates Event Sourcing + Railway Oriented Programming.
   When an order is created, an event is published to Kafka
   and appended to the event store."
  (:require [clojure.spec.alpha :as s]
            [com.borba.be-boilerplate.specs.order :as specs]
            [com.borba.be-boilerplate.repository.order :as repo]
            [borba.kafka-producer :as kafka]
            [borba.event-store :as es]
            [com.borba.be-boilerplate.util.railway :as rop])
  (:import (java.util UUID)))

;; ── Railway steps ───────────────────────────────────────────────────────────

(defn- validate-create-input
  "Validates order creation input."
  [input]
  (if (s/valid? ::specs/create-input input)
    (rop/right input)
    (rop/left {:error   :validation-failed
               :details (s/explain-str ::specs/create-input input)})))

(defn- calculate-total
  "Calculates the order total from items."
  [input]
  (let [total (->> (:items input)
                   (map #(* (:quantity %) (:price %)))
                   (reduce +))]
    (rop/right (assoc input :total total))))

(defn- enrich-order
  "Adds generated ID and default status."
  [input]
  (rop/right
   (assoc input
          :id     (str (UUID/randomUUID))
          :status :pending)))

(defn- persist-order
  "Persists order to database."
  [components]
  (fn [order]
    (rop/try-right repo/insert! components order)))

(defn- publish-event
  "Publishes order-created event to Kafka and appends to event store."
  [components]
  (fn [order]
    (try
      ;; Append to event store
      (let [{:keys [event-store kafka-producer]} components
            order-id (:id order)
            version  (inc (es/get-latest-version event-store order-id))]
        (es/append! event-store
                    {:aggregate-id   order-id
                     :aggregate-type "order"
                     :event-type     "order-created"
                     :payload        order
                     :version        version})
        ;; Publish to Kafka
        (when kafka-producer
          (kafka/send! kafka-producer "order-events" order-id order)))
      (rop/right order)
      (catch Exception e
        ;; Event publishing failure is non-blocking
        (println "⚠️  Event publish warning:" (.getMessage e))
        (rop/right order)))))

;; ── Public API ──────────────────────────────────────────────────────────────

(defn create!
  "Creates a new order.
   
   Pipeline:
     validate → calculate-total → enrich → persist → publish-event
   
   Returns Right{:id :user-id :items :total :status} or Left{:error ...}"
  [components input]
  (rop/|> input
          validate-create-input
          calculate-total
          enrich-order
          (persist-order components)
          (publish-event components)))

(defn get-by-id
  "Retrieves an order by ID."
  [components id]
  (if-let [order (repo/find-by-id components id)]
    (rop/right order)
    (rop/left {:error :not-found
               :id    id})))
