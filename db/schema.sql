CREATE TABLE PERSON (
  ID INT  PRIMARY KEY  AUTO_INCREMENT,
  FIRST_NAME VARCHAR(255),
  LAST_NAME VARCHAR(255),
  EMAIL VARCHAR(255),
  MOBILE_PHONE VARCHAR(255),
  VERIFIED BOOLEAN,
  CREATED_DATE TIME
);
