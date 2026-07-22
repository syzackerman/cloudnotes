create table attachments (
    id uuid primary key,
    note_id uuid not null references notes(id) on delete cascade,
    user_id uuid not null references users(id) on delete cascade,
    original_filename varchar(255) not null,
    storage_key varchar(1024) not null unique,
    content_type varchar(255),
    size_bytes bigint not null,
    created_at timestamp(6) with time zone not null
);

create index idx_attachments_note_id on attachments(note_id);
create index idx_attachments_user_id on attachments(user_id);
create index idx_attachments_created_at on attachments(created_at);
create index idx_attachments_note_user_created_at on attachments(note_id, user_id, created_at asc);
