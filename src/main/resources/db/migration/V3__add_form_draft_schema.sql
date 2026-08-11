-- Published form versions are immutable. Editable structure lives on the form draft.
ALTER TABLE application_form
    ADD COLUMN draft_schema_json JSON NULL AFTER max_submissions_per_user;

UPDATE application_form
SET draft_schema_json = JSON_OBJECT('fields', JSON_ARRAY())
WHERE draft_schema_json IS NULL;

ALTER TABLE application_form
    MODIFY COLUMN draft_schema_json JSON NOT NULL;
