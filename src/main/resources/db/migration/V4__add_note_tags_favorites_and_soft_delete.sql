alter table notes
    add column favorite boolean not null default false,
    add column deleted boolean not null default false,
    add column deleted_at timestamp(6) with time zone;

create table tags (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id) on delete cascade,
    name varchar(80) not null,
    normalized_name varchar(80) not null,
    created_at timestamp(6) with time zone not null,
    constraint uk_tags_user_normalized_name unique (user_id, normalized_name)
);

create table note_tags (
    note_id uuid not null references notes(id) on delete cascade,
    tag_id uuid not null references tags(id) on delete cascade,
    primary key (note_id, tag_id)
);

create index idx_notes_user_deleted_updated_at on notes(user_id, deleted, updated_at desc);
create index idx_notes_user_deleted_favorite on notes(user_id, deleted, favorite);
create index idx_tags_user_normalized_name on tags(user_id, normalized_name);
create index idx_note_tags_tag_id_note_id on note_tags(tag_id, note_id);
