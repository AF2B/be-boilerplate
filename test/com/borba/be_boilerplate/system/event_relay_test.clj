(ns com.borba.be-boilerplate.system.event-relay-test
  "Unit tests for the pure steps of the event relay."
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.borba.be-boilerplate.system.event-relay :as relay])
  (:import
   (java.time Instant)))

(set! *warn-on-reflection* true)

(def ^:private now (Instant/parse "2026-10-22T12:00:00Z"))

(defn- event
  [position aggregate-type seconds-ago]
  {:position       position
   :aggregate-id   (str "agg-" position)
   :aggregate-type aggregate-type
   :event-type     "order-created"
   :payload        {:n position}
   :created-at     (.minusSeconds ^Instant now seconds-ago)})

(defn- recorder
  "A send! that keeps what it is given, and throws for the keys it is told."
  [failing-keys]
  (let [sent (atom [])]
    {:sent  sent
     :send! (fn [topic message-key value]
              (when (contains? failing-keys message-key)
                (throw (ex-info "broker away" {})))
              (swap! sent conj [topic message-key value]))}))

(deftest settled-test
  (testing "keeps the events up to the first one newer than the cutoff"
    (let [events [(event 1 "order" 10) (event 2 "order" 5)
                  (event 3 "order" 0) (event 4 "order" 9)]]
      (is (= [1 2]
             (map :position (relay/settled events
                                           (.minusSeconds ^Instant now 2))))))))

(deftest publish-all-test
  (testing "publishes in order, as envelopes keyed by the aggregate"
    (let [{:keys [sent send!]} (recorder #{})
          progress (relay/publish-all! send!
                                       {"order" "order-events"}
                                       [(event 1 "order" 9) (event 2 "order" 9)]
                                       0)]
      (is (= {:position 2 :published 2} progress))
      (is (= [["order-events" "agg-1" {:event-type "order-created"
                                       :data       {:n 1}}]
              ["order-events" "agg-2" {:event-type "order-created"
                                       :data       {:n 2}}]]
             @sent))))

  (testing "passes over the events of a type without a topic"
    (let [{:keys [sent send!]} (recorder #{})
          progress (relay/publish-all! send!
                                       {"order" "order-events"}
                                       [(event 5 "user" 9) (event 6 "order" 9)]
                                       4)]
      (is (= {:position 6 :published 1} progress))
      (is (= ["agg-6"] (map second @sent)))))

  (testing "stops at the first event that fails, and stays before it"
    (let [{:keys [sent send!]} (recorder #{"agg-2"})
          progress (relay/publish-all! send!
                                       {"order" "order-events"}
                                       [(event 1 "order" 9)
                                        (event 2 "order" 9)
                                        (event 3 "order" 9)]
                                       0)]
      (is (= 1 (:position progress)))
      (is (= 1 (:published progress)))
      (is (= "broker away" (ex-message (:failure progress))))
      (is (= ["agg-1"] (map second @sent))))))
