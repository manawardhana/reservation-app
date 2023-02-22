-- src/reservation_app/db/sql/db.sql
-- The Reservation App


-- ------- PERSON -------

-- :name create-person-table
-- :command :execute
-- :result :raw
-- :doc Create PERSON table
--  auto_increment and current_timestamp are
--  H2 Database specific (adjust to your DB)
create table person (
  "id" int  primary key  auto_increment,
  "first-name" varchar(255) not null,
  "last-name" varchar(255) not null,
  "email" varchar(255),
  "password" varchar(255),
  "otp" varchar(255),
  "post-code" varchar(4),
  "can-log-in" boolean not null default false,
  "mobile-phone" varchar(25),
  "verified" boolean not null,
  "deleted" boolean not null default false,
  "created-at"  timestamp not null default current_timestamp
);

-- :name drop-person-table :!
-- :doc Drop person table if exists
drop table if exists person;

-- :name insert-person :! :n
-- :doc Insert a single person returning affected row count
insert into person ("first-name", "last-name", "password", "post-code", "email", "mobile-phone", "verified")
values (:first-name, :last-name, :password, :post-code, :email, :mobile-phone, :verified)

-- :name clj-expr-generic-update :! :n
/* :require [clojure.string :as string]
            [hugsql.parameters :refer [identifier-param-quote]] */
update :i:table set -- leave space here
/*~
(string/join ","
  (for [[field _] (:updates params)]
    (str "\"" (identifier-param-quote (name field) options) "\""
      " = :v:updates." (name field))))
~*/
 where "id" = :id -- leading space is important!

---- -- :name update-person :! :n
---- -- :doc Update a single person returning affected row count
----
---- update person
---- set
----   "first-name"=:first-name,
----   "last-name"=:last-name,
----   email=:email,
----   "mobile-phone"=:mobile-phone,
----   "post-code":post-code
----   verified=:verified
----
---- where id=:id;

-- :name person-by-id :? :1
-- :doc Get person by id
select * from person
 where "id" = :id

-- :name person-by-email :? :1
-- :doc Get person by id
select * from person
 where "email" = :email

-- :name person-by-mobile-phone :? :1
-- :doc Get person by id
select * from person
 where "mobile-phone" = :mobile-phone

-- (as a hashmap) will be returned
-- :name list-person :? :*
select * from person limit :limit offset :offset


-- ------- APPOINTMENT_REQUEST -------

-- :name create-appointment-request-table
-- :command :execute
-- :result :raw
-- :doc Create appointment_request table
--  auto_increment and current_timestamp are
--  H2 Database specific (adjust to your DB)
create table appointment_request (
  "id" int  primary key  auto_increment,
  "apt-date" date not null,
  "booking-option" varchar(255),
  "requesters-comments" text,
  "status" varchar(255),
  "requested-by" int references person("id"),
  "approved-by" int references person("id"),
  "created-at"  timestamp not null default current_timestamp
);

-- :name drop-appointment-request-table :!
-- :doc Drop appointment_request table if exists
drop table if exists appointment_request;

-- A :result value of :n below will return affected rows:
-- :name insert-appointment-request :! :n
-- :doc Insert a single appointment-request returning affected row count
insert into appointment_request ("apt-date", "booking-option", "requesters-comments", "status", "requested-by", "approved-by")
 values (:apt-date, :booking-option, :requesters-comments, :status, :requested-by, :approved-by);


-- (as a hashmap) will be returned
-- :name list-booking-request :? :*

select "id", "apt-date", "booking-option", "requesters-comments", "status", "requested-by", "approved-by"
from appointment-request where "apt-date" between :from-date and :to-date;

-- ------- APPOINTMENT_SLOT_EVENT -------

-- :name create-appointment-slot-event-table
-- :command :execute
-- :result :raw
-- :doc Create appointment_slot_event table
--  auto_increment and current_timestamp are
--  H2 Database specific (adjust to your DB)
create table appointment_slot_event (
  "id" int  primary key  auto_increment,
  "apt-date" date not null,
  "slot-name" varchar(255),
  "event-type" varchar(255) not null,
  "event-detail" varchar(255),
  "user-comments" varchar(255),
  "subject-id" int references appointment_slot_event("id"),
  "user-id" int references person("id"),
  "event-time" timestamp not null default current_timestamp
);

-- :name drop-appointment-slot-event-table :!
-- :doc Drop appointment_request table if exists
drop table if exists appointment_slot_event;

-- A :result value of :n below will return affected rows:
-- :name insert-appointment-slot-event :! :n
-- :doc Insert a single appointment-slot-event returning affected row count
insert into appointment-slot-event ("apt-date", "slot-name" ,"event-type", "event-detail", "user-comments", "subject-id", "user-id")
values (:apt-date, :slot-name, :event-type, :event-detail, :user-comments, :subject-id, :user-id);
