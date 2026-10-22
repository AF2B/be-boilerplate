(ns com.borba.be-boilerplate.handlers.business.user-test
  "Unit tests for User business logic."
  (:require
   [borba.railway :as rop]
   [clojure.test :refer [deftest is testing]]
   [clojure.test.check.clojure-test :as tc]
   [clojure.test.check.properties :as prop]
   [com.borba.be-boilerplate.handlers.business.user :as user]
   [generators.user :as gen-user]))

(def ^:private property-trials 50)

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
    (let [result (#'user/validate-create-input {:name  "Test"
                                                :email "not-an-email"})]
      (is (rop/left? result))
      (is (= :validation-failed (:error (rop/unwrap result))))))

  (testing "blank name returns Left"
    (let [result (#'user/validate-create-input {:name  "   "
                                                :email "test@test.com"})]
      (is (rop/left? result)))))

(tc/defspec generated-input-is-valid property-trials
  (prop/for-all [input gen-user/gen-user-input]
    (rop/right? (#'user/validate-create-input input))))

;; ── Enrichment and lookup ───────────────────────────────────────────────────

(deftest enrich-user-test
  (testing "adds a UUID id and the creation time"
    (let [enriched (rop/unwrap (#'user/enrich-user {:name "Ana"}))]
      (is (uuid? (:id enriched)))
      (is (inst? (:created-at enriched))))))

(deftest get-by-id-test
  (testing "an id that is not a UUID is not found, without a query"
    (let [result (user/get-by-id {} "42")]
      (is (= {:error :not-found :id "42"} (rop/unwrap result))))))

;; ── Railway utility tests ───────────────────────────────────────────────────

(deftest railway-operations-test
  (testing "failure builds a Left with the error code and its context"
    (is (= {:error :user/email-already-exists :email "a@b.com"}
           (rop/unwrap (rop/failure :user/email-already-exists
                                    {:email "a@b.com"})))))

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

  (testing "a step that returns a plain value is a programming error"
    (is (thrown? clojure.lang.ExceptionInfo
                 (rop/>>= (rop/right 1) inc)))))
