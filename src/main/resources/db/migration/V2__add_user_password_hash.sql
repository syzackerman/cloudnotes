alter table users
    add column password_hash varchar(255) not null default '';

alter table users
    alter column password_hash drop default;
