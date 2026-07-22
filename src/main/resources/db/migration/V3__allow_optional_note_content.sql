alter table notes
    alter column title type varchar(200),
    alter column content drop not null;
