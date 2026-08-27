-- Columns the entities map but no migration created.
--
-- These are the fields that make a citation checkable. A retrieved chunk that
-- cannot say which page and section it came from produces an answer a reviewer
-- has to take on trust, which defeats the point of citing sources at all.

-- ---------------------------------------------------------------------------
-- Chunk provenance
-- ---------------------------------------------------------------------------
ALTER TABLE document_chunks
    ADD COLUMN IF NOT EXISTS page_number INTEGER;

ALTER TABLE document_chunks
    ADD COLUMN IF NOT EXISTS section_title VARCHAR(255);

ALTER TABLE document_chunks
    ADD COLUMN IF NOT EXISTS paragraph_index INTEGER;

-- Similarity of this chunk to the query that retrieved it. Transient per search
-- rather than a stored property, but the entity carries it, so the column has to
-- exist for Hibernate to map the row.
ALTER TABLE document_chunks
    ADD COLUMN IF NOT EXISTS relevance_score DOUBLE PRECISION;

-- Citations are rendered "Document, page N, section X"; ordering by position
-- inside the document keeps them readable.
CREATE INDEX IF NOT EXISTS idx_document_chunk_position
    ON document_chunks (document_id, page_number, paragraph_index)
    WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- Question section
-- ---------------------------------------------------------------------------
-- Questionnaires are grouped into sections ("Access Control", "Encryption"), and
-- the review queue groups by them. vanta_questions had category and
-- sub_category but never section, which the entity has always mapped.
ALTER TABLE vanta_questions
    ADD COLUMN IF NOT EXISTS section VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_vanta_question_section
    ON vanta_questions (questionnaire_id, section)
    WHERE deleted_at IS NULL;
