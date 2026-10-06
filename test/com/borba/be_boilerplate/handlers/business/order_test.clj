(ns com.borba.be-boilerplate.handlers.business.order-test
  "Unit tests for Order business logic."
  (:require [clojure.test :refer [deftest testing is]]
            [com.borba.be-boilerplate.handlers.business.order :as order]
            [borba.railway :as rop]))

(deftest validate-order-input-test
  (testing "valid input returns Right"
    (let [input  {:user-id "550e8400-e29b-41d4-a716-446655440000"
                  :items   [{:product  "Widget"
                             :quantity 2
                             :price    29.99}]}
          result (#'order/validate-create-input input)]
      (is (rop/right? result))
      (is (= input (rop/unwrap result)))))

  (testing "missing user-id returns Left"
    (let [result (#'order/validate-create-input
                  {:items [{:product "X" :quantity 1 :price 10}]})]
      (is (rop/left? result))))

  (testing "empty items returns Left"
    (let [result (#'order/validate-create-input
                  {:user-id "550e8400-e29b-41d4-a716-446655440000"
                   :items   []})]
      (is (rop/left? result))))

  (testing "item with negative quantity returns Left"
    (let [result (#'order/validate-create-input
                  {:user-id "550e8400-e29b-41d4-a716-446655440000"
                   :items   [{:product "X" :quantity -1 :price 10}]})]
      (is (rop/left? result)))))

(deftest calculate-total-test
  (testing "calculates total correctly"
    (let [input  {:items [{:product "A" :quantity 2 :price 10.0}
                          {:product "B" :quantity 1 :price 5.0}]}
          result (#'order/calculate-total input)]
      (is (rop/right? result))
      (is (= 25.0 (:total (rop/unwrap result))))))

  (testing "single item total"
    (let [input  {:items [{:product "A" :quantity 3 :price 7.50}]}
          result (#'order/calculate-total input)]
      (is (rop/right? result))
      (is (= 22.5 (:total (rop/unwrap result)))))))
