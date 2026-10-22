(ns com.borba.be-boilerplate.handlers.business.order-test
  "Unit tests for Order business logic."
  (:require
   [borba.railway :as rop]
   [clojure.test :refer [deftest is testing]]
   [clojure.test.check.clojure-test :as tc]
   [clojure.test.check.properties :as prop]
   [com.borba.be-boilerplate.handlers.business.order :as order]
   [generators.order :as gen-order]))

(def ^:private user-id "550e8400-e29b-41d4-a716-446655440000")

(def ^:private property-trials 50)

(deftest validate-order-input-test
  (testing "valid input returns Right"
    (let [input  {:user-id user-id
                  :items   [{:product  "Widget"
                             :quantity 2
                             :price    29.99}]}
          result (#'order/validate-create-input input)]
      (is (rop/right? result))
      (is (= input (rop/unwrap result)))))

  (testing "missing user-id returns Left"
    (let [result (#'order/validate-create-input
                  {:items [{:product "X" :quantity 1 :price 10}]})]
      (is (rop/left? result))
      (is (= :validation-failed (:error (rop/unwrap result))))))

  (testing "empty items returns Left"
    (let [result (#'order/validate-create-input
                  {:user-id user-id
                   :items   []})]
      (is (rop/left? result))))

  (testing "item with negative quantity returns Left"
    (let [result (#'order/validate-create-input
                  {:user-id user-id
                   :items   [{:product "X" :quantity -1 :price 10}]})]
      (is (rop/left? result)))))

(deftest calculate-total-test
  (testing "calculates the total as a BigDecimal"
    (let [input  {:items [{:product "A" :quantity 2 :price 10.0}
                          {:product "B" :quantity 1 :price 5.0}]}
          total  (:total (rop/unwrap (#'order/calculate-total input)))]
      (is (decimal? total))
      (is (== 25 total))))

  (testing "keeps the cents that a double would lose"
    (let [input {:items [{:product "A" :quantity 3 :price 0.1}]}
          total (:total (rop/unwrap (#'order/calculate-total input)))]
      (is (== 0.3M total)))))

(deftest enrich-order-test
  (testing "adds an id, the user id as a UUID and the pending status"
    (let [order (rop/unwrap (#'order/enrich-order {:user-id user-id
                                                   :items   []}))]
      (is (uuid? (:id order)))
      (is (= (parse-uuid user-id) (:user-id order)))
      (is (= :pending (:status order))))))

(deftest get-by-id-test
  (testing "an id that is not a UUID is not found, without a query"
    (let [result (order/get-by-id {} "not-a-uuid")]
      (is (rop/left? result))
      (is (= {:error :not-found :id "not-a-uuid"} (rop/unwrap result))))))

(tc/defspec generated-input-is-valid property-trials
  (prop/for-all [input gen-order/gen-order-input]
    (rop/right? (#'order/validate-create-input input))))

(tc/defspec total-is-the-sum-of-the-lines property-trials
  (prop/for-all [input gen-order/gen-order-input]
    (let [expected (reduce + (map #(* (:quantity %) (:price %)) (:items input)))
          total    (:total (rop/unwrap (#'order/calculate-total input)))]
      (== expected total))))
