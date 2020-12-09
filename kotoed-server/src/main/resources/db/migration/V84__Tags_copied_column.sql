alter table submission
    add tags_copied boolean default false not null;

UPDATE submission
SET tags_copied = true;
