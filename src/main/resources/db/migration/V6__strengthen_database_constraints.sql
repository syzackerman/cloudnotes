alter table notes
    add constraint uk_notes_id_user_id unique (id, user_id);

alter table tags
    add constraint uk_tags_id_user_id unique (id, user_id);

alter table attachments
    add constraint fk_attachments_note_user
        foreign key (note_id, user_id)
        references notes (id, user_id)
        on delete cascade,
    add constraint ck_attachments_size_bytes_non_negative
        check (size_bytes >= 0);
