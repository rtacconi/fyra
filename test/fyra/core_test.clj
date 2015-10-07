(ns fyra.core-test
  (:require [midje.sweet :refer :all]
            [fyra.core :as f]
            [fyra.relational :as r]
            [fyra.test-app :refer (reset-app) :as app]
            [clojure.set :as set]
            [clojure.string :as string]))

(facts "about fyra"
  (facts "about basic use of relval and insert/select operations"
    (reset-app)
    (fact "select TodoList gives all lists"
      (f/select app/TodoList) => app/all-lists)
    (fact "select TodoItem gives all items"
      (f/select app/TodoItem) => app/all-items))
  (facts "about dynamic use of insert, update and delete"
    (fact "inserting works"
      (let [item {:list "project-2" :content "Get approval" :done? false}]
        (f/insert app/TodoItem item)
        (f/select app/TodoItem) => (conj app/all-items item)))
    (fact "updating all in a relation works"
      (let [updtr #(update-in % [:title] str "UPDATED")]
        (f/update app/TodoList updtr)
        (f/select app/TodoList) => (set (map updtr app/all-lists))))
    (fact "deleting all in a relation works"
      (f/delete app/TodoItem)
      (f/select app/TodoItem) => #{}))
  (facts "about selecting using relational algebra"
    (reset-app)
    (fact "project works for a single key"
      (f/select (r/project app/TodoList :id))
        => (set (map #(select-keys % [:id]) app/all-lists)))
    (fact "project works for multiple keys"
      (f/select (r/project app/TodoList :title :color))
        => (set (map #(select-keys % [:title :color]) app/all-lists)))
    (fact "project-away works for multiple keys"
      (f/select (r/project-away app/TodoList :title :color))
        => (set (map #(select-keys % [:id]) app/all-lists)))
    (fact "extend creates new keys"
      (let [first-word #(-> % :content (string/split #" ") first)]
        (f/select (r/extend app/TodoItem :first-word first-word))
          => (set (map #(assoc % :first-word (first-word %)) app/all-items))))
    (facts "about restrict"
      (fact "restrict to done items"
        (f/select (r/restrict app/TodoItem :done?))
          => (set (filter :done? app/all-items)))
      (fact "restrict to project-1 items"
        (f/select (r/restrict app/TodoItem #(= (:list %) "project-1"))) => app/project-1-items)
      (fact "restrict nothing"
        (f/select (r/restrict app/TodoList #(do % true))) => app/all-lists)
      (fact "restrict everything"
        (f/select (r/restrict app/TodoItem #(do % false))) => #{}))
    (facts "about summarize"
      (fact "find number of lists by color"
        (f/select (r/summarize app/TodoList [:color] {:num-items count}))
          => #{{:color "red" :num-items 1}
               {:color "blue" :num-items 2}})
      (fact "find total number of items without a grouping"
        (f/select (r/summarize app/TodoItem [] {:num-items count}))
          => #{{:num-items (count app/all-items)}}))
    (facts "about join"
      (fact "join is commutative"
        (f/select (r/join app/TodoList app/TodoItem))
          => (f/select (r/join app/TodoItem app/TodoList)))
      (fact "join works with foreign keys"
        (f/select (r/join app/TodoList app/TodoItem))
          => (set/join app/all-lists app/all-items {:id :list})))
    (facts "about minus"
      (fact "minus will take out appropriate items"
        (f/select (r/minus app/TodoItem
                           (r/restrict app/TodoItem :done?)))
          => (set (filter #(not (:done? %)) app/all-items))))
    (facts "about combining multiple algebraic functions"
      (fact "find max number of lists by color"
        (f/select
          (r/summarize (r/summarize app/TodoList [:color] {:num-items count})
                       [] #(r/maximum-key :num-items %)))
            => #{{:color "blue" :num-items 2}})
      (fact "find number of items in a list"
        (f/select (r/summarize (r/restrict app/TodoItem #(= (:list %) "home"))
                               [:list] {:num-items count}))
          => #{{:list "home" :num-items (count app/home-items)}})
      (fact "Find number of items in colored lists"
        (f/select (r/summarize (r/join app/TodoList app/TodoItem)
                               [:color]
                               {:num-items count}))
          => #{{:color "red" :num-items (count app/home-items)}
               {:color "blue" :num-items (+ (count app/project-1-items)
                                            (count app/project-2-items))}})))
    (facts "about updating and deleting using relational algebra"
      (fact "updating a single list works"
        (let [g #(update-in % [:title] str "UPDATED")]
          (f/update (r/restrict app/TodoList #(= (:id %) "home")) g)
          (f/select (r/restrict app/TodoList #(= (:id %) "home")))
            => #{(g app/home-list)}))
      (fact "updating a multiple items works"
        (let [g #(assoc-in % [:done?] true)]
          (f/update (r/restrict app/TodoItem #(= (:list %) "home")) g)
          (f/select (r/restrict app/TodoItem #(= (:list %) "home")))
            => (set (map g app/home-items))))
      (fact "deleting a single list works"
        (f/delete (r/restrict app/TodoList #(= (:id %) "project-1")))
        (f/select (r/restrict app/TodoList #(= (:id %) "project-1")))
          => #{})
      (fact "deleting a multiple items works"
        (f/delete (r/restrict app/TodoItem #(= (:list %) "project-1")))
        (f/select (r/restrict app/TodoItem #(= (:list %) "project-1")))
          => #{})))