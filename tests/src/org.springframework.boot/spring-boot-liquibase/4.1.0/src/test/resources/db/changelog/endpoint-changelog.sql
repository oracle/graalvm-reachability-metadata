--liquibase formatted sql

--changeset test:create-account
CREATE TABLE account (id INT PRIMARY KEY);
