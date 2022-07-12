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
  id int  primary key  auto_increment,
  "first-name" varchar(255) not null,
  "last-name" varchar(255) not null,
  email varchar(255),
  password varchar(255),
  "password_salt" varchar(255),
  "can-log-in" boolean not null default false,
  "mobile-phone" varchar(25),
  verified boolean not null,
  deleted boolean not null default false,
  "created-at"  timestamp not null default current_timestamp
);

-- :name drop-person-table :!
-- :doc Drop person table if exists
drop table if exists person;

-- A :result value of :n below will return affected rows:
-- :name insert-person :! :n
-- :doc Insert a single person returning affected row count
insert into person ("first-name", "last-name", email, "mobile-phone", verified)
values (:first-name, :last-name, :email, :mobile-phone, :verified)

-- (as a hashmap) will be returned
-- :name person-by-id :? :1
-- :doc Get person by id
select * from person
where id = :id

-- ------- APPOINTMENT_REQUEST -------

-- :name create-appointment-request-table
-- :command :execute
-- :result :raw
-- :doc Create appointment_request table
--  auto_increment and current_timestamp are
--  H2 Database specific (adjust to your DB)
create table appointment_request (
  id int  primary key  auto_increment,
  apt_date date not null,
  slot_name varchar(255),
  requesters_comments text,
  status varchar(255),
  approved_by int references person(id)
);

-- :name drop-appointment-request-table :!
-- :doc Drop appointment_request table if exists
drop table if exists appointment_request;

-- A :result value of :n below will return affected rows:
-- :name insert-appointment-request :! :n
-- :doc Insert a single appointment-request returning affected row count
insert into appointment_request (apt_date, slot_name, requesters_comments, status, approved_by)
values (:apt-date, :slot-name, :requesters-comments, :status, :approved-by)


-- ------- APPOINTMENT_SLOT_EVENT -------

-- :name create-appointment-slot-event-table
-- :command :execute
-- :result :raw
-- :doc Create appointment_slot_event table
--  auto_increment and current_timestamp are
--  H2 Database specific (adjust to your DB)
create table appointment_slot_event (
  id int  primary key  auto_increment,
  apt_date date not null,
  slot_name varchar(255),
  event_type varchar(255) not null,
  user_comments varchar(255),
  subject_id int references appointment_slot_event(id),
  user_id int references person(id),
  event_time timestamp not null default current_timestamp
);

-- :name drop-appointment-slot-event-table :!
-- :doc Drop appointment_request table if exists
drop table if exists appointment_slot_event;
