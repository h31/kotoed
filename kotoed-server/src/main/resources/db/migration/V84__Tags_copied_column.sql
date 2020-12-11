ALTER TABLE submission
    ADD tags_copied BOOLEAN DEFAULT FALSE NOT NULL;

UPDATE submission
SET tags_copied = TRUE
WHERE NOT EXISTS(SELECT * FROM submission_tag WHERE submission_id = submission.id);
