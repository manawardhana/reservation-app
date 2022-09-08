(ns reservation-app.calendar
  (:import [java.time LocalDate Month DayOfWeek]
           [java.time.format DateTimeFormatter]))

(defn day-seq
  ([] (day-seq (LocalDate/now)))
  ([d] (lazy-seq (cons d (day-seq (.plusDays d 1))))))

(take 5 (day-seq))

(map #(.toString %) (into [] (Month/values)))
