(ns crux.lucene-test
  (:require [clojure.test :as t]
            [crux.api :as c]
            [crux.codec :as cc]
            [crux.db :as db]
            [crux.fixtures :as fix :refer [*api* submit+await-tx with-tmp-dir]]
            [crux.lucene :as l]
            [crux.memory :as mem]
            [crux.node :as n])
  (:import org.apache.lucene.analysis.Analyzer
           [org.apache.lucene.document Document Field StoredField TextField]
           [org.apache.lucene.index IndexWriter IndexWriterConfig]
           org.apache.lucene.store.Directory))

(defn with-lucene-module [f]
  (with-tmp-dir "lucene" [db-dir]
    (fix/with-opts {::l/node {:db-dir (.toPath ^java.io.File db-dir)}}
      f)))

(t/use-fixtures :each with-lucene-module fix/with-node)

(t/deftest test-can-search-string
  (let [doc {:crux.db/id :ivan :name "Ivan"}
        {:keys [^Directory directory ^Analyzer analyzer]} (:crux.lucene/node @(:!system *api*))
        iwc (IndexWriterConfig. analyzer)]

    (submit+await-tx [[:crux.tx/put {:crux.db/id :ivan :name "Ivan"}]])

    ;; Index
    (with-open [iw (IndexWriter. directory, iwc)]
      (.addDocument iw (l/crux-doc->lucene-doc doc)))

    (t/testing "using Lucene directly"
      (with-open [search-results ^crux.api.ICursor (l/search *api* "name" "Ivan")]
        (let [docs (iterator-seq search-results)]
          (t/is (= 1 (count docs)))
          (t/is (= "Ivan" (.get ^Document (first docs) "name"))))))

    (t/testing "using predicate function"
      (with-open [db (c/open-db *api*)]
        (t/is (= #{[:ivan]} (c/q db {:find '[?e]
                                     :where '[[(crux.lucene/full-text node db :name "Ivan") [[?e]]]
                                              [?e :crux.db/id]]
                                     :args [{:db db :node *api* :?foo-fn identity}]})))))))

;; TODO:
;;  Cardinality search
;;  Transactions
;;  Decorate Crux node
