(ns build
  "Builds the service uberjar. The library name and the main namespace come
   from the :build alias of deps.edn; the version from APP_VERSION, otherwise
   from the latest git tag."
  (:require
   [clojure.edn :as edn]
   [clojure.string :as str]
   [clojure.tools.build.api :as b]))

(set! *warn-on-reflection* true)

(def ^:private class-dir "target/classes")
(def ^:private source-dirs ["src"])

(defn- build-options
  "Returns the :exec-args of the :build alias of deps.edn."
  []
  (get-in (edn/read-string (slurp "deps.edn"))
          [:aliases :build :exec-args]))

(defn- describe-version
  "Returns the version to build: APP_VERSION when set, otherwise the latest git
   tag without its leading v, otherwise dev."
  []
  (or (System/getenv "APP_VERSION")
      (some-> (b/git-process {:git-args ["describe" "--tags" "--always"]})
              (str/replace #"^v" ""))
      "dev"))

(defn clean
  "Removes the build output.
   - _options: ignored"
  [_options]
  (b/delete {:path "target"}))

(defn uber
  "Compiles the main namespace and writes the uberjar, and returns its path.
   - options: overrides of the :build alias (:lib, :main-ns, :uber-jar)"
  [options]
  (let [{:keys [lib main-ns uber-jar]} (merge (build-options) options)
        version (describe-version)
        basis   (b/create-basis {:project "deps.edn"})]
    (clean nil)
    (b/copy-dir {:src-dirs   source-dirs
                 :target-dir class-dir})
    (b/compile-clj {:basis      basis
                    :ns-compile [main-ns]
                    :class-dir  class-dir})
    (b/uber {:class-dir class-dir
             :uber-file uber-jar
             :basis     basis
             :main      main-ns
             :manifest  {"Implementation-Title"   (name lib)
                         "Implementation-Version" version}})
    (println "built" uber-jar "version" version)
    uber-jar))
