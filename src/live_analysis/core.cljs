(ns live-analysis.core
  (:require [cljs.js :as cljs]
            [cljs.pprint]
            cljsjs.codemirror
            cljsjs.codemirror.mode.clojure
            [cljsjs.parinfer]
            [cljsjs.parinfer-codemirror]
            [goog.string :as gstring]
            [goog.string.format]
            [reagent.core :as r]
            [cljs.analyzer :as ana]))

(enable-console-print!)

(defonce app-state (r/atom {:output ""}))

(set! (.. js/window -cljs -user) #js {})

(defn elide-env [env ast opts]
  (dissoc ast :env))

(def st (cljs/empty-state))

(defn analyze! [form-string cb]
  (cljs/analyze-str st
                    form-string
                    nil
                    {:passes [ana/infer-type elide-env]}
                    cb))


(defn main []
  [:div.columns
   [:div.column
    [:div#editor-container]]
   [:div.column.is-three-quarters
    [:div#ast-container
     {:style {:height "100%"}}]]])

(def order-of-ast-keys
  [:form :segs :op :js-op :tag :numeric :args :children])

(defn sorted-ast
  [ast]
  (into (sorted-map-by (fn [key1 key2]
                         (compare (-indexOf order-of-ast-keys key1)
                                  (-indexOf order-of-ast-keys key2))))
        ast))

(def initial-form-string
  "(inc 1)")

(defonce loaded
  (atom false))

(when-not @loaded

  (reset! loaded true)

  (analyze! initial-form-string
            (fn [{:keys [value]}]
              (swap! app-state
                     assoc
                     :output
                     (with-out-str (cljs.pprint/pprint (sorted-ast value))))))

  (r/render-component [main]
                      (. js/document (getElementById "app")))


  (let [editor-container (js/document.getElementById "editor-container")
        editor-cm        (js/CodeMirror editor-container
                                        (clj->js {"theme" "monokai"
                                                  "value" initial-form-string}))
        ast-container    (js/document.getElementById "ast-container")
        ast-cm           (js/CodeMirror ast-container
                                        (clj->js {"theme"    "monokai"
                                                  "readOnly" true
                                                  "value"    (or (:output @app-state)
                                                                 "Loading…")}))]
    (js/parinferCodeMirror.init ast-cm)
    (js/parinferCodeMirror.init editor-cm)
    (.on editor-cm "change" (fn [cm change]
                              (let [doc (.getDoc cm)]
                                (analyze! (.getValue doc)
                                          (fn [{:keys [error value] :as res}]
                                            (let [sorted-ast-map (sorted-ast value)]
                                              (if-not error
                                                (.setValue (.getDoc ast-cm)
                                                           (with-out-str (cljs.pprint/pprint sorted-ast-map)))
                                                (.error js/console error))))))))))


