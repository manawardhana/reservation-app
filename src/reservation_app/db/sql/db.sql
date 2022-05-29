-- src/reservation_app/db/sql/db.sql
-- The Reservation App

-- :name create-person-table
-- :command :execute
-- :result :raw
-- :doc Create PERSON table
--  auto_increment and current_timestamp are
--  H2 Database specific (adjust to your DB)
CREATE TABLE PERSON (
  ID INT  PRIMARY KEY  AUTO_INCREMENT,
  FIRST_NAME VARCHAR(255),
  LAST_NAME VARCHAR(255),
  EMAIL VARCHAR(255),
  MOBILE_PHONE VARCHAR(255),
  VERIFIED BOOLEAN,
  CREATED_AT  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);


-- A :result value of :n below will return affected rows:
-- :name insert-person :! :n
-- :doc Insert a single person returning affected row count
INSERT INTO PERSON (FIRST_NAME, LAST_NAME, EMAIL, MOBILE_PHONE, VERIFIED)
VALUES (:first-name, :last-name, :email, :mobile-phone, :verified)
