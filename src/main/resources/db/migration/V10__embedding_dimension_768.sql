-- Match the embedding columns to the model that actually produces them.
--
-- Every vector column was declared vector(1536), which is OpenAI
-- text-embedding-ada-002's dimension. The configured model is
-- nomic-embed-text, which produces 768. Postgres rejects the mismatch outright:
--
--     ERROR: expected 1536 dimensions, not 768
--
-- so no embedding could ever have been stored, and retrieval could never have
-- returned anything.
--
-- The dimension is a property of the model, not a preference. Changing embedding
-- models means another migration AND re-embedding every row -- vectors from
-- different models are not comparable, and mixing them silently produces
-- confident nonsense rather than an error.

-- Existing vectors cannot be reinterpreted at a new dimension, and there are
-- none to preserve (nothing could be inserted), so drop and recreate.
DROP INDEX IF EXISTS idx_answer_library_embedding;
DROP INDEX IF EXISTS idx_document_chunks_embedding;
DROP INDEX IF EXISTS idx_documents_embedding;

ALTER TABLE documents           ALTER COLUMN embedding TYPE vector(768) USING NULL;
ALTER TABLE document_chunks     ALTER COLUMN embedding TYPE vector(768) USING NULL;
ALTER TABLE vanta_answer_library ALTER COLUMN embedding TYPE vector(768) USING NULL;

-- Recreate the approximate-nearest-neighbour indexes at the new dimension.
CREATE INDEX idx_document_chunks_embedding
    ON document_chunks USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE INDEX idx_answer_library_embedding
    ON vanta_answer_library USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 50);
