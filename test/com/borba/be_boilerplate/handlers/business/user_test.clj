(ns com.borba.be-boilerplate.handlers.business.user-test
  "Unit tests for User business logic."
  (:require [clojure.test :refer [deftest testing is]]
            [com.borba.be-boilerplate.handlers.business.user :as user]
            [borba.railway :as rop]))

;; ── Validation tests ────────────────────────────────────────────────────────

(deftest validate-create-input-test
  (testing "valid input returns Right"
    (let [input  {:name "André Borba" :email "andre@borba.com"}
          result (#'user/validate-create-input input)]
      (is (rop/right? result))
      (is (= input (rop/unwrap result)))))

  (testing "missing name returns Left"
    (let [result (#'user/validate-create-input {:email "test@test.com"})]
      (is (rop/left? result))
      (is (= :validation-failed (:error (rop/unwrap result))))))

  (testing "invalid email returns Left"
    (let [result (#'user/validate-create-input {:name "Test" :email "not-an-email"})]
      (is (rop/left? result))
      (is (= :validation-failed (:error (rop/unwrap result))))))

  (testing "blank name returns Left"
    (let [result (#'user/validate-create-input {:name "   " :email "test@test.com"})]
      (is (rop/left? result)))))

;; ── Railway utility tests ───────────────────────────────────────────────────

(deftest railway-operations-test
  (testing "Right wrapping and unwrapping"
    (let [r (rop/right {:data 42})]
      (is (rop/right? r))
      (is (= {:data 42} (rop/unwrap r)))))

  (testing "Left wrapping and unwrapping"
    (let [l (rop/left {:error :something})]
      (is (rop/left? l))
      (is (= {:error :something} (rop/unwrap l)))))

  (testing "bind short-circuits on Left"
    (let [result (rop/>>= (rop/left {:error :first})
                          (fn [_] (rop/right :should-not-reach)))]
      (is (rop/left? result))
      (is (= {:error :first} (rop/unwrap result)))))

  (testing "bind chains Rights"
    (let [result (rop/>>= (rop/right 1)
                          (fn [x] (rop/right (inc x)))
                          (fn [x] (rop/right (* x 10))))]
      (is (rop/right? result))
      (is (= 20 (rop/unwrap result)))))

  (testing "either dispatches correctly"
    (is (= :success
           (rop/either (rop/right 42)
                       (fn [_] :failure)
                       (fn [_] :success))))
    (is (= :failure
           (rop/either (rop/left {:error :oops})
                       (fn [_] :failure)
                       (fn [_] :success))))))
