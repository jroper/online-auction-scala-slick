-- This file is used to create databases and users and grant permissions for development.
-- It should not be used in production, firstly because it drops the databases first,
-- which could be dangerous, secondly the priveleges it grants are too broad, it expects
-- the tables to be created automatically by the app, but this shouldn't be used in
-- production, instead, they should be explicitly created.

DROP DATABASE IF EXISTS online_auction_bidding;
DROP USER IF EXISTS online_auction_bidding;

DROP DATABASE IF EXISTS online_auction_item;
DROP USER IF EXISTS online_auction_item;

DROP DATABASE IF EXISTS online_auction_user;
DROP USER IF EXISTS online_auction_user;

DROP DATABASE IF EXISTS online_auction_transaction;
DROP USER IF EXISTS online_auction_transaction;

DROP DATABASE IF EXISTS online_auction_search;
DROP USER IF EXISTS online_auction_search;

CREATE DATABASE online_auction_bidding;
CREATE USER online_auction_bidding WITH PASSWORD 'online_auction_bidding';
GRANT ALL PRIVILEGES ON DATABASE online_auction_bidding to online_auction_bidding;

CREATE DATABASE online_auction_item;
CREATE USER online_auction_item WITH PASSWORD 'online_auction_item';
GRANT ALL PRIVILEGES ON DATABASE online_auction_item to online_auction_item;

CREATE DATABASE online_auction_user;
CREATE USER online_auction_user WITH PASSWORD 'online_auction_user';
GRANT ALL PRIVILEGES ON DATABASE online_auction_user to online_auction_user;

CREATE DATABASE online_auction_transaction;
CREATE USER online_auction_transaction WITH PASSWORD 'online_auction_transaction';
GRANT ALL PRIVILEGES ON DATABASE online_auction_transaction to online_auction_transaction;

CREATE DATABASE online_auction_search;
CREATE USER online_auction_search WITH PASSWORD 'online_auction_search';
GRANT ALL PRIVILEGES ON DATABASE online_auction_search to online_auction_search;
