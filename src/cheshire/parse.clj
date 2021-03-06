(ns cheshire.parse
  (:import (org.codehaus.jackson JsonParser JsonToken)))

(declare parse*)

(def ^{:doc "Flag to determine whether float values should be returned as
             BigDecimals to retain precision. Defaults to false."
       :dynamic true}
  *use-bigdecimals?* false)

(definline parse-object [^JsonParser jp keywords? bd? array-coerce-fn]
  `(do
     (.nextToken ~jp)
     (loop [mmap# (transient {})]
       (if-not (= (.getCurrentToken ~jp)
                  JsonToken/END_OBJECT)
         (let [key-str# (.getText ~jp)
               _# (.nextToken ~jp)
               key# (if ~keywords?
                      (keyword key-str#)
                      key-str#)
               mmap# (assoc! mmap# key#
                             (parse* ~jp ~keywords? ~bd? ~array-coerce-fn))]
           (.nextToken ~jp)
           (recur mmap#))
         (persistent! mmap#)))))

(definline parse-array [^JsonParser jp keywords? bd? array-coerce-fn]
  `(let [array-field-name# (.getCurrentName ~jp)]
     (.nextToken ~jp)
     (loop [coll# (transient (if ~array-coerce-fn
                               (~array-coerce-fn array-field-name#)
                               []))]
       (if-not (= (.getCurrentToken ~jp)
                  JsonToken/END_ARRAY)
         (let [coll# (conj! coll#(parse* ~jp ~keywords? ~bd? ~array-coerce-fn))]
           (.nextToken ~jp)
           (recur coll#))
         (persistent! coll#)))))

(defn parse* [^JsonParser jp keywords? bd? array-coerce-fn]
  (condp = (.getCurrentToken jp)
    JsonToken/START_OBJECT (parse-object jp keywords? bd? array-coerce-fn)
    JsonToken/START_ARRAY (parse-array jp keywords? bd? array-coerce-fn)
    JsonToken/VALUE_STRING (.getText jp)
    JsonToken/VALUE_NUMBER_INT (.getNumberValue jp)
    JsonToken/VALUE_NUMBER_FLOAT (if bd?
                                   (.getDecimalValue jp)
                                   (.getNumberValue jp))
    JsonToken/VALUE_TRUE true
    JsonToken/VALUE_FALSE false
    JsonToken/VALUE_NULL nil
    (throw
     (Exception.
      (str "Cannot parse " (pr-str (.getCurrentToken jp)))))))

(defn parse [^JsonParser jp fst? keywords? eof array-coerce-fn]
  (let [keywords? (boolean keywords?)]
    (.nextToken jp)
    (if (nil? (.getCurrentToken jp))
      eof
      (parse* jp keywords? *use-bigdecimals?* array-coerce-fn))))
