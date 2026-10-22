(ns com.borba.be-boilerplate.handlers.http.handlers-test
  "Unit tests for the health handlers, which are functions of data."
  (:require
   [borba.core.lifecycle :as lifecycle]
   [clojure.test :refer [deftest is testing]]
   [com.borba.be-boilerplate.handlers.http.handlers :as handlers]))

(deftest health-check-test
  (testing "liveness is always UP"
    (is (= 200 (:status (handlers/health-check {}))))))

(deftest readiness-test
  (testing "a service that is still starting is not ready"
    (let [response (handlers/readiness
                    {:components {:lifecycle (lifecycle/create)}})]
      (is (= 503 (:status response)))
      (is (= :starting (get-in response [:body :status])))))

  (testing "a ready lifecycle without a database is not ready"
    (let [state    (lifecycle/mark-ready! (lifecycle/create))
          response (handlers/readiness {:components {:lifecycle state}})]
      (is (= 503 (:status response)))
      (is (false? (get-in response [:body :database])))))

  (testing "a draining service is not ready"
    (let [state    (-> (lifecycle/create)
                       lifecycle/mark-ready!
                       lifecycle/begin-drain!)
          response (handlers/readiness {:components {:lifecycle state}})]
      (is (= 503 (:status response)))
      (is (= :draining (get-in response [:body :status]))))))
